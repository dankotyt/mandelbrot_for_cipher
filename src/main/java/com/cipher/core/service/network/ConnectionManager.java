package com.cipher.core.service.network;

import com.cipher.core.model.PeerInfo;
import com.cipher.client.handler.ClientConnectionHandler;
import com.cipher.client.handler.ClientConnectionHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Менеджер сетевых соединений пиров.
 * <p>
 * Отвечает за:
 * <ul>
 *   <li>Управление состоянием подключенных пиров</li>
 *   <li>Персистентное хранение информации о последнем подключении</li>
 *   <li>Запуск/остановку сервера обмена ключами</li>
 *   <li>Обработку входящих соединений через фабрику хендлеров</li>
 * </ul>
 *
 * Класс является Spring-компонентом и управляет жизненным циклом соединений.
 * Использует пул потоков для асинхронной обработки подключений.
 *
 * @author Cipher Team
 * @version 1.0
 * @see KeyExchangeService
 * @see ClientConnectionHandlerFactory
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionManager {

    private final KeyExchangeService keyExchangeService;
    private final ClientConnectionHandlerFactory handlerFactory;

    /** Карта активных подключений к пирам с их статусом и метаданными */
    private final Map<InetAddress, PeerInfo> connectedPeers = new ConcurrentHashMap<>();

    /** Пул потоков для асинхронной обработки входящих соединений */
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

    /** Флаг состояния менеджера (запущен/остановлен) */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Серверный сокет для приема входящих запросов на обмен ключами */
    private ServerSocket keyExchangeServerSocket;

    /** Поток сервера для возможности graceful shutdown */
    private Thread serverThread;

    /**
     * Хранилище персистентных соединений.
     * Сохраняет информацию о пирах даже после временного разрыва связи.
     * Ключ - строковое представление IP-адреса.
     */
    private final Map<String, InetAddress> persistentConnectedPeers = new ConcurrentHashMap<>();

    /** Последний успешно подключенный пир (используется для быстрого доступа) */
    private InetAddress lastConnectedPeer;

    /**
     * Устанавливает текущего активного пира.
     * Сохраняет информацию в персистентное хранилище и обновляет последнее подключение.
     *
     * @param peerAddress IP-адрес пира, или null для сброса
     */
    public void setConnectedPeer(InetAddress peerAddress) {
        this.lastConnectedPeer = peerAddress;
        if (peerAddress != null) {
            persistentConnectedPeers.put(peerAddress.getHostAddress(), peerAddress);
        }
        log.info("✅ Установлен подключенный пир: {}",
                peerAddress != null ? peerAddress.getHostAddress() : "null");
    }

    /**
     * Возвращает текущего активного пира.
     * <p>
     * Алгоритм поиска:
     * <ol>
     *   <li>Проверяет последний подключенный пир - если активен, возвращает его</li>
     *   <li>Ищет любой активный пир в персистентном хранилище</li>
     *   <li>Возвращает null, если активных пиров нет</li>
     * </ol>
     *
     * @return InetAddress активного пира или null
     */
    public InetAddress getConnectedPeer() {
        // Сначала пробуем получить последний подключенный пир
        if (lastConnectedPeer != null &&
                keyExchangeService.isConnectedTo(lastConnectedPeer)) {
            return lastConnectedPeer;
        }

        // Если последний не доступен, ищем любого доступного пира
        for (InetAddress peer : persistentConnectedPeers.values()) {
            if (keyExchangeService.isConnectedTo(peer)) {
                lastConnectedPeer = peer; // Обновляем последний доступный
                log.info("🔄 Восстановлен подключенный пир из хранилища: {}",
                        peer.getHostAddress());
                return peer;
            }
        }

        log.warn("❌ Нет доступных подключенных пиров в хранилище");
        return null;
    }

    /**
     * Получает список всех активных подключенных пиров.
     * Фильтрует персистентное хранилище, оставляя только живые соединения.
     *
     * @return List&lt;InetAddress&gt; список IP-адресов активных пиров
     */
    public List<InetAddress> getAllConnectedPeers() {
        return persistentConnectedPeers.values().stream()
                .filter(keyExchangeService::isConnectedTo)
                .collect(Collectors.toList());
    }

    /**
     * Удаляет пир из персистентного хранилища.
     * Используется при разрыве соединения или отклонении запроса.
     *
     * @param peerAddress IP-адрес пира для удаления
     */
    public void removePeer(InetAddress peerAddress) {
        if (peerAddress != null) {
            persistentConnectedPeers.remove(peerAddress.getHostAddress());
            if (peerAddress.equals(lastConnectedPeer)) {
                lastConnectedPeer = null;
            }
            log.info("🗑️ Удален пир из хранилища: {}", peerAddress.getHostAddress());
        }
    }

    /**
     * Запускает менеджер соединений.
     * <p>
     * Инициализирует:
     * <ul>
     *   <li>Сервер обмена ключами</li>
     *   <li>Пул потоков для обработки соединений</li>
     * </ul>
     * Метод потокобезопасен - повторный вызов не создает новый сервер.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startKeyExchangeServer();
            log.info("Connection manager started");
        }
    }

    /**
     * Останавливает менеджер соединений.
     * <p>
     * Выполняет graceful shutdown:
     * <ul>
     *   <li>Останавливает сервер обмена ключами</li>
     *   <li>Закрывает все активные соединения</li>
     *   <li>Завершает пул потоков</li>
     * </ul>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            stopKeyExchangeServer();
            closeAllConnections();
            connectionExecutor.shutdown();
            try {
                if (!connectionExecutor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    connectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Connection manager stopped");
        }
    }

    /**
     * Закрывает все активные соединения с пирами.
     * <p>
     * Последовательность действий:
     * <ol>
     *   <li>Отправляет всем пирам уведомление об инвалидации ключей</li>
     *   <li>Закрывает соединения через KeyExchangeService</li>
     *   <li>Очищает локальный кэш connectedPeers</li>
     * </ol>
     */
    public void closeAllConnections() {
        if (!connectedPeers.isEmpty()) {
            log.info("Closing all connections...");

            // Создаем копию для безопасной итерации
            Map<InetAddress, PeerInfo> peersToClose = new ConcurrentHashMap<>(connectedPeers);

            // Оповещаем пиров об инвалидации ключей
            peersToClose.keySet().forEach(keyExchangeService::sendKeyInvalidation);

            // Закрываем соединения
            peersToClose.keySet().forEach(peer -> {
                connectedPeers.remove(peer);
                keyExchangeService.closeConnection(peer);
            });

            log.info("All connections closed");
        }
    }

    /**
     * Запускает сервер для приема входящих запросов на обмен ключами.
     * <p>
     * Сервер работает на порту {@code 8889} (KEY_EXCHANGE_PORT).
     * Установлен таймаут на accept() для возможности прерывания потока.
     * Каждое входящее соединение обрабатывается в отдельном потоке из пула.
     */
    private void startKeyExchangeServer() {
        serverThread = new Thread(() -> {
            try {
                keyExchangeServerSocket = new ServerSocket(8889); // KEY_EXCHANGE_PORT
                keyExchangeServerSocket.setSoTimeout(1000);
                log.info("Key exchange server started on port 8889");

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = keyExchangeServerSocket.accept();
                        log.info("Incoming connection from: {}",
                                clientSocket.getInetAddress().getHostAddress());

                        ClientConnectionHandler handler = handlerFactory.createHandler(clientSocket);
                        connectionExecutor.submit(handler);

                    } catch (SocketTimeoutException e) {
                        // Нормальное поведение при таймауте - продолжаем цикл
                        // Таймаут нужен для проверки running flag
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("Error accepting connection: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Key exchange server error: {}", e.getMessage());
                }
            } finally {
                closeServerSocket();
                log.info("Key exchange server stopped");
            }
        }, "KeyExchange-Server");

        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Останавливает сервер обмена ключами.
     * Прерывает поток сервера и закрывает серверный сокет.
     * Ожидает завершения потока до 2 секунд.
     */
    private void stopKeyExchangeServer() {
        if (serverThread != null) {
            serverThread.interrupt();
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeServerSocket();
    }

    /**
     * Закрывает серверный сокет, если он открыт.
     * Подавляет исключения при закрытии.
     */
    private void closeServerSocket() {
        if (keyExchangeServerSocket != null && !keyExchangeServerSocket.isClosed()) {
            try {
                keyExchangeServerSocket.close();
            } catch (Exception e) {
                log.warn("Error closing server socket: {}", e.getMessage());
            }
        }
    }
}
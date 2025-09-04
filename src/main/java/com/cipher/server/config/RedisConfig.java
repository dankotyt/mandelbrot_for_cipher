package com.cipher.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Конфигурация Redis для хранения временных данных.
 * Настраивает подключение к Redis серверу и шаблоны для работы с данными.
 */
@Configuration
public class RedisConfig {

    /**
     * Создает фабрику подключений к Redis.
     * Использует standalone конфигурацию с localhost и портом 6379.
     *
     * @return настроенная фабрика подключений Redis
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);

        return new LettuceConnectionFactory(config);
    }

    /**
     * Создает шаблон для работы с Redis данными.
     * Настраивает сериализацию ключей и значений в строки.
     *
     * @param connectionFactory фабрика подключений к Redis
     * @return настроенный RedisTemplate для строковых операций
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}

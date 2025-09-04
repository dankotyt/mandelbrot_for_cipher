package com.cipher.server.repository;

import com.cipher.common.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью User в базе данных.
 * Предоставляет методы для выполнения CRUD операций и пользовательских запросов.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Находит пользователя по его уникальному идентификатору.
     *
     * @param userId идентификатор пользователя для поиска
     * @return Optional с найденным пользователем или empty если не найден
     */
    Optional<User> findByUserId(String userId);

    /**
     * Проверяет существование пользователя с указанным идентификатором.
     *
     * @param userId идентификатор пользователя для проверки
     * @return true если пользователь существует, false в противном случае
     */
    boolean existsByUserId(String userId);
}

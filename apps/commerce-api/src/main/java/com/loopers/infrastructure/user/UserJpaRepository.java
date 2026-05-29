package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserModel, UUID> {
    boolean existsByLoginIdValue(String value);
    Optional<UserModel> findByLoginIdValue(String value);
}

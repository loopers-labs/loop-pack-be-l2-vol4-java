package com.loopers.infrastructure.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.user.UserModel;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Optional<UserModel> findByLoginIdValue(String value);

    boolean existsByLoginIdValue(String value);

    boolean existsByEmailValue(String value);
}

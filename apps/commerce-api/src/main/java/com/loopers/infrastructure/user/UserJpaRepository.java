package com.loopers.infrastructure.user;

import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(LoginId loginId);

    boolean existsByLoginId(LoginId loginId);

    boolean existsByEmail(Email email);
}

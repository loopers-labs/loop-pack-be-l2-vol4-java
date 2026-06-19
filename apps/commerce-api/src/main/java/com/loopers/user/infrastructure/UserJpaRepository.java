package com.loopers.user.infrastructure;

import com.loopers.user.domain.User;
import com.loopers.user.domain.vo.LoginId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(LoginId loginId);
}

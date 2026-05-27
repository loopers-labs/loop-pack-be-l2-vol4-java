package com.loopers.user.infrastructure;

import com.loopers.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    boolean existsUserByLoginId(String loginId);
    Optional<User> findByLoginId(String loginId);
}

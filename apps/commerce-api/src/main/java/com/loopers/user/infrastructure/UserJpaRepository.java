package com.loopers.user.infrastructure;

import com.loopers.user.domain.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByLoginId(String loginId);
}

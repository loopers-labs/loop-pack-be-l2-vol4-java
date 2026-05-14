package com.loopers.infrastructure.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserModel, Long> {

    Optional<UserModel> findByLoginId(LoginId loginId);

    boolean existsByLoginId(LoginId loginId);
}

package com.loopers.infrastructure.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }

    @Override
    public Optional<UserModel> findByLoginIdValue(String loginIdValue) {
        return userJpaRepository.findByLoginIdValue(loginIdValue);
    }

    @Override
    public UserModel save(UserModel user) {
        return userJpaRepository.save(user);
    }
}

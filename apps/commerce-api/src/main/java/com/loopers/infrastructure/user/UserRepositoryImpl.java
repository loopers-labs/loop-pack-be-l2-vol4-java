package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<UserModel> findById(Long userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public Optional<UserModel> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }
}

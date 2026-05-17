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
    public UserModel save(UserModel user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<UserModel> findByIdForUpdate(Long id) {
        return userJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<UserModel> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }
}

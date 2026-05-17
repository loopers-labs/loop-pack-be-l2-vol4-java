package com.loopers.infrastructure.user;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

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
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValue(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginIdValue(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailValue(email);
    }
}

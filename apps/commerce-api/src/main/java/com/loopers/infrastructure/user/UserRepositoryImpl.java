package com.loopers.infrastructure.user;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

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
    public UserModel getActiveById(Long id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));
    }

    @Override
    public Optional<UserModel> findActiveByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValueAndDeletedAtIsNull(loginId);
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

package com.loopers.infrastructure.user;

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
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValue(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginIdValue(loginId);
    }

    @Override
    public UserModel save(UserModel userModel) {
        return userJpaRepository.save(userModel);
    }
}

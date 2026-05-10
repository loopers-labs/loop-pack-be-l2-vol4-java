package com.loopers.infrastructure.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId.value());
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId.value());
    }
}

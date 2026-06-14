package com.loopers.user.infrastructure;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.vo.LoginId;
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
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public Optional<User> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }
}

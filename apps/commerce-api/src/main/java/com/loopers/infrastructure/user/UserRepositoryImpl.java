package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsUserByLoginId(loginId);
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }
}

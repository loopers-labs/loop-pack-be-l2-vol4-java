package com.loopers.infrastructure.user;

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
        UserJpaEntity userJpaEntity = user.getId() == null
            ? UserJpaEntity.from(user)
            : userJpaRepository.findById(user.getId())
                .map(existingUser -> {
                    existingUser.update(user);
                    return existingUser;
                })
                .orElseGet(() -> UserJpaEntity.from(user));

        return userJpaRepository.save(userJpaEntity).toDomain();
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginId(loginId)
            .map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }
}

package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserEntity;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserEntity save(UserEntity user) {
        return UserMapper.toDomain(userJpaRepository.save(UserMapper.toJpaEntity(user)));
    }

    @Override
    public Optional<UserEntity> findByUserId(String userId) {
        return userJpaRepository.findByUserId(userId).map(UserMapper::toDomain);
    }
}

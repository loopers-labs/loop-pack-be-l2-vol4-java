package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel saveAndFlush(UserModel user) {
        return userJpaRepository.saveAndFlush(user);
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginIdValue(loginId);
    }
}

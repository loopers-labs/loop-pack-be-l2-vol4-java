package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public boolean existsByUserid(String userid) {
        return userJpaRepository.existsByUserid(userid);
    }

    @Override
    public UserModel save(UserModel user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<UserModel> findByUserid(String userid) {
        return userJpaRepository.findByUserid(userid);
    }
}

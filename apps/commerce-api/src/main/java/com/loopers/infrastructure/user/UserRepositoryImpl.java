package com.loopers.infrastructure.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel userModel){
        return userJpaRepository.save(userModel);
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId.getValue());
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<UserModel> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId.getValue());
    }
}

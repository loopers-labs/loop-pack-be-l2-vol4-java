package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public UserModel save(UserModel user) {
        try {
            return userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<UserModel> findByUserId(String userId) {
        return userJpaRepository.findByUserId(userId);
    }

}

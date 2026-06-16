package com.loopers.user.application;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.user.domain.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResult.Detail getUser(Long userId) {
        return UserResult.Detail.from(get(userId));
    }

    private User get(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, UserErrorCode.USER_NOT_FOUND));
    }
}

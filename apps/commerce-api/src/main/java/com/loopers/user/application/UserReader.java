package com.loopers.user.application;

import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.user.domain.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserReader {

    private final UserRepository userRepository;

    public void ensureExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, UserErrorCode.USER_NOT_FOUND);
        }
    }
}

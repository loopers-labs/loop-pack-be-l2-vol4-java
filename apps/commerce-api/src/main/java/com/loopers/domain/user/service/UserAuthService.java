package com.loopers.domain.user.service;

import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if (!passwordEncryptor.matches(rawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserModel getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
    }
}

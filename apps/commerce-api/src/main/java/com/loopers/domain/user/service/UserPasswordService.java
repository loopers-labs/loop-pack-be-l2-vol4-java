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
public class UserPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        UserModel user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
        if (!passwordEncryptor.matches(currentRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncryptor.matches(newRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.");
        }
        String newEncoded = passwordEncryptor.encode(newRawPassword, user.getBirthDate());
        user.changePassword(newEncoded);
    }
}

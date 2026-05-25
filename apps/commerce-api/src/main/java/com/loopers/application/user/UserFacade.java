package com.loopers.application.user;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserFacade {

    private final UserRepository userRepository;
    private final PasswordEncrypter passwordEncrypter;

    public UserSignUpInfo signUp(String rawLoginId, String rawPassword, String rawName, LocalDate rawBirthDate, String rawEmail) {
        if (userRepository.existsByLoginId(rawLoginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        if (userRepository.existsByEmail(rawEmail)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        UserModel newUser = UserModel.builder()
            .rawLoginId(rawLoginId)
            .rawPassword(rawPassword)
            .rawName(rawName)
            .rawBirthDate(rawBirthDate)
            .rawEmail(rawEmail)
            .passwordEncrypter(passwordEncrypter)
            .build();

        return UserSignUpInfo.from(userRepository.save(newUser));
    }

    @Transactional(readOnly = true)
    public UserMyInfo readMyInfo(Long userId) {
        return UserMyInfo.from(mustFindUserById(userId));
    }

    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        UserModel user = mustFindUserById(userId);
        user.changePassword(currentRawPassword, newRawPassword, passwordEncrypter);
    }

    private UserModel mustFindUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));
    }
}

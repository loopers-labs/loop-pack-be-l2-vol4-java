package com.loopers.domain.user;

import java.time.LocalDate;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncrypter passwordEncrypter;

    public UserModel signUp(String rawLoginId, String rawPassword, String rawName, LocalDate rawBirthDate, String rawEmail) {
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

        return userRepository.save(newUser);
    }
}

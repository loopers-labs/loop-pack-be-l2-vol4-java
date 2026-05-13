package com.loopers.domain.user;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncrypter passwordEncrypter;

    private final UserRepository userRepository;

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

    @Transactional(readOnly = true)
    public UserModel readMyInfo(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));
    }

    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        UserModel user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원이 존재하지 않습니다."));

        user.changePassword(currentRawPassword, newRawPassword, passwordEncrypter);
    }
}

package com.loopers.domain.user.service;

import com.loopers.domain.user.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserSignupService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public UserModel signup(String rawLoginId, String rawPassword, String rawName, LocalDate birthDate, String rawEmail) {
        LoginId loginId = new LoginId(rawLoginId);
        if (userRepository.existsByLoginId(loginId.getValue())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 유저입니다.");
        }
        String encoded = passwordEncryptor.encode(rawPassword, birthDate);
        UserModel user = new UserModel(loginId, encoded, new UserName(rawName), birthDate, new Email(rawEmail));
        return userRepository.save(user);
    }
}

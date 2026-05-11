package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        UserPasswordPolicy.validate(rawPassword, birthDate);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = UserModel.builder()
            .loginId(loginId)
            .encodedPassword(encodedPassword)
            .name(name)
            .birthDate(birthDate)
            .email(email)
            .build();
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }
}
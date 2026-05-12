package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(LoginId loginId, Name name, Birth birth, Email email, String rawPassword) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 이메일입니다.");
        }

        Password password = Password.of(rawPassword, birth);
        String encodedPassword = passwordEncoder.encode(password.value());

        User user = new User(loginId, name, birth, email, encodedPassword);
        return userRepository.save(user);
    }
}

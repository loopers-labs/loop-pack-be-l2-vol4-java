package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signUp(LoginId loginId, Password password, String name, BirthDate birthDate, Email email) {
        password.requireNotContaining(birthDate);
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 입니다.");
        }
        Password encodedPassword = Password.encoded(passwordEncoder.encode(password.getValue()));
        UserModel user = UserModel.create(loginId, encodedPassword, name, birthDate, email);
        return userRepository.save(user);
    }
}

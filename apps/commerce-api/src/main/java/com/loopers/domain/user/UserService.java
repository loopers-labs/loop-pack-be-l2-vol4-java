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
    private final PasswordHasher passwordHasher;

    @Transactional
    public UserModel signUp(String loginId, String password, String name, String birthDate, String email, Gender gender) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        return userRepository.save(new UserModel(loginId, password, name, birthDate, email, gender, passwordHasher));
    }
}

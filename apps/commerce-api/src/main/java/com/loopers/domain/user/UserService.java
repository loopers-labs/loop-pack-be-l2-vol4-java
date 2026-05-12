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
    public UserModel signUp(String loginId, String rawPassword, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + loginId + "] 이미 사용 중인 ID 입니다.");
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = new UserModel(loginId, encodedPassword, name, birthDate, email);
        return userRepository.save(user);
    }
}

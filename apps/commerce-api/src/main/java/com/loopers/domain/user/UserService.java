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
    public User register(LoginId loginId, String rawPassword, Name name, Birth birth, Email email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 이메일입니다.");
        }

        User user = User.register(loginId, rawPassword, name, birth, email, passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginId loginId, String password) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return user;
    }

    @Transactional
    public void changePassword(LoginId loginId, String currentPassword, String newPassword) {
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        user.changePassword(currentPassword, newPassword, passwordEncoder);
    }
}

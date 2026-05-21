package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel register(UserRegistrationCommand command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        UserModel userModel = new UserModel(command);
        userModel.encodePassword(passwordEncoder);
        return userRepository.save(userModel);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return user;
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel managed = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
        managed.changePassword(currentPassword, newPassword, passwordEncoder);
    }
}

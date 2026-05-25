package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    public User join(String loginId, String loginPassword, String name, LocalDate birthday, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용중인 로그인 아이디입니다.");
        }

        PasswordValidator.validate(loginPassword, birthday);

        User user = new User(loginId, passwordEncryptor.encrypt(loginPassword), name, birthday, email);
        return userRepository.save(user);
    }

    public User getUser(String loginId, String loginPassword) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncryptor.matches(loginPassword, user.getLoginPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다.");
        }

        return user;
    }

    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordEncryptor.matches(oldPassword, user.getLoginPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncryptor.matches(newPassword, user.getLoginPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이전 비밀번호와 동일하게 설정할 수 없습니다.");
        }

        PasswordValidator.validate(newPassword, user.getBirthday());

        user.changePassword(passwordEncryptor.encrypt(newPassword));
        userRepository.save(user);
    }
}

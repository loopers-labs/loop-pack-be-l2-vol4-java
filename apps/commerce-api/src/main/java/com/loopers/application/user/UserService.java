package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserPasswordPolicy;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserReader userReader;

    @Transactional
    public User signUp(UserCommand.SignUp command) {
        validateSignUp(command);

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(
            command.loginId(),
            encodedPassword,
            command.name(),
            command.birthDate(),
            command.email()
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<Long> authenticate(String loginId, String rawPassword) {
        return userRepository.findByLoginId(loginId)
            .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
            .map(User::getId);
    }

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return userReader.get(userId);
    }

    @Transactional
    public void changePassword(UserCommand.ChangePassword command) {
        User user = userReader.get(command.userId());
        validateChangePassword(user, command.currentPassword(), command.newPassword());
        user.changePassword(passwordEncoder.encode(command.newPassword()));
    }

    private void validateSignUp(UserCommand.SignUp command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        if (UserPasswordPolicy.containsBirthDate(command.password(), command.birthDate())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private void validateChangePassword(User user, String currentRawPassword, String newRawPassword) {
        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (UserPasswordPolicy.containsBirthDate(newRawPassword, user.getBirthDate())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}

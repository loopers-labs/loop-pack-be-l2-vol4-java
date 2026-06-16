package com.loopers.user.application;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserPasswordPolicy;
import com.loopers.user.domain.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.user.domain.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResult.Detail signUp(UserCommand.SignUp command) {
        validateSignUp(command);

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(
            command.loginId(),
            encodedPassword,
            command.name(),
            command.birthDate(),
            command.email()
        );
        return UserResult.Detail.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Optional<Long> authenticate(String loginId, String rawPassword) {
        return userRepository.findByLoginId(loginId)
            .filter(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
            .map(User::getId);
    }

    @Transactional
    public void changePassword(UserCommand.ChangePassword command) {
        User user = get(command.userId());
        validateChangePassword(user, command.currentPassword(), command.newPassword());
        user.changePassword(passwordEncoder.encode(command.newPassword()));
    }

    private User get(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, UserErrorCode.USER_NOT_FOUND));
    }

    private void validateSignUp(UserCommand.SignUp command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, UserErrorCode.LOGIN_ID_DUPLICATED);
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new CoreException(ErrorType.CONFLICT, UserErrorCode.EMAIL_DUPLICATED);
        }
        if (UserPasswordPolicy.containsBirthDate(command.password(), command.birthDate())) {
            throw new CoreException(ErrorType.BAD_REQUEST, UserErrorCode.PASSWORD_CONTAINS_BIRTHDATE);
        }
    }

    private void validateChangePassword(User user, String currentRawPassword, String newRawPassword) {
        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (UserPasswordPolicy.containsBirthDate(newRawPassword, user.getBirthDate())) {
            throw new CoreException(ErrorType.BAD_REQUEST, UserErrorCode.PASSWORD_CONTAINS_BIRTHDATE);
        }
    }
}

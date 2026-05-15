package com.loopers.domain.user;

import com.loopers.domain.user.command.SignUpUserCommand;
import com.loopers.domain.user.vo.EncodedPassword;
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
    public UserModel signUp(SignUpUserCommand signUpUserCommand) {
        if (userRepository.findByLoginId(signUpUserCommand.loginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }

        EncodedPassword encodedPassword = passwordHasher.hash(signUpUserCommand.plainPassword());
        UserModel user = UserModel.signUp(
            signUpUserCommand.loginId(),
            encodedPassword,
            signUpUserCommand.name(),
            signUpUserCommand.birthDate(),
            signUpUserCommand.email()
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserModel user = getUser(userId);
        user.changePassword(currentPassword, newPassword, passwordHasher);
    }
}
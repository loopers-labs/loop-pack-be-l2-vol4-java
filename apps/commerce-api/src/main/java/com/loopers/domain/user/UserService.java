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
    public UserModel signUp(UserCommand.SignUp command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }
        PasswordPolicy.validate(command.rawPassword(), command.birthDate());
        EncodedPassword encodedPassword = EncodedPassword.create(passwordEncoder, command.rawPassword());
        UserModel user = new UserModel(
            command.loginId(),
            command.name(),
            command.birthDate(),
            command.email(),
            encodedPassword
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(UserCommand.Authenticate command) {
        return findAuthenticated(command.loginIdInput(), command.rawPasswordInput());
    }

    @Transactional
    public void changePassword(UserCommand.ChangePassword command) {
        UserModel user = findAuthenticated(command.loginIdInput(), command.authPasswordInput());
        if (user.doesNotMatchPassword(command.currentRawPassword(), passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        PasswordPolicy.validate(command.newRawPassword(), user.getBirthDate());
        if (user.matchesPassword(command.newRawPassword(), passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        user.changePassword(passwordEncoder, command.newRawPassword());
    }

    private UserModel findAuthenticated(String loginIdInput, String rawPasswordInput) {
        if (loginIdInput == null || rawPasswordInput == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        UserModel user = userRepository.findByLoginIdValue(loginIdInput)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if (user.doesNotMatchPassword(rawPasswordInput, passwordEncoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        return user;
    }
}

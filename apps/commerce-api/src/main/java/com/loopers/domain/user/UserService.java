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
        String encodedPassword = passwordEncoder.encode(command.rawPassword());
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
        if (command.loginIdInput() == null || command.rawPasswordInput() == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        UserModel user = userRepository.findByLoginIdValue(command.loginIdInput())
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if (!passwordEncoder.matches(command.rawPasswordInput(), user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        return user;
    }
}

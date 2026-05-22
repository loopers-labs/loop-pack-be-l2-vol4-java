package com.loopers.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserValidator userValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(UserCommand.SignUp command) {
        userValidator.validateSignUp(command);

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
}

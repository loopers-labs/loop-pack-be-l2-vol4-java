package com.loopers.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserValidator userValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserReader userReader;

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
    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        User user = userReader.get(userId);
        userValidator.validateChangePassword(user, currentRawPassword, newRawPassword);
        user.changePassword(passwordEncoder.encode(newRawPassword));
    }
}

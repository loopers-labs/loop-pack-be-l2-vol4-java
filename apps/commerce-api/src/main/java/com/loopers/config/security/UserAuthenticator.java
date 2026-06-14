package com.loopers.config.security;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserAuthenticator {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<Long> authenticate(String loginId, String rawPassword) {
        if (rawPassword == null) {
            return Optional.empty();
        }

        return LoginId.tryParse(loginId)
            .flatMap(userRepository::findByLoginId)
            .filter(user -> matchesPassword(rawPassword, user))
            .map(User::getId);
    }

    private boolean matchesPassword(String rawPassword, User user) {
        return passwordEncoder.matches(rawPassword, user.getPassword().value());
    }
}

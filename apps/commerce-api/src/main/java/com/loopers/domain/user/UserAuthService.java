package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserModel authenticate(LoginId loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));
        if (!passwordEncoder.matches(rawPassword, user.getPassword().getValue())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return user;
    }
}

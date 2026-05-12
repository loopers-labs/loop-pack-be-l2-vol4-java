package com.loopers.domain.user;

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

    @Transactional(readOnly = true)
    public Optional<UserModel> findByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId);
    }

    @Transactional
    public UserModel signUp(UserModel user) {
        userRepository.findByLoginId(user.getLoginId())
            .ifPresent(existing -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 ID입니다.");
            });
        user.encodePassword(passwordEncoder::encode);
        return userRepository.save(user);
    }
}

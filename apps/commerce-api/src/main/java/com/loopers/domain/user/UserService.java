package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Transactional
    public UserModel register(UserModel user) {
        passwordPolicy.validate(user.getPassword(), user.getBirthDate());
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        user.changePassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(UserModel user, String currentRawPassword, String newRawPassword) {
        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        passwordPolicy.validate(newRawPassword, user.getBirthDate());
        if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        user.changePassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.");
        }
        return user;
    }
}

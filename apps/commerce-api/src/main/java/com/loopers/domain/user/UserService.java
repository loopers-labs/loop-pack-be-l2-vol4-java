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

    @Transactional
    public User createUser(String loginId, String rawPassword) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        return userRepository.save(new User(loginId, encodedPassword));
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + id + "] 유저를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public User getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "유저를 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(Long userId, String currentPw, String newPw) {
        User user = getUser(userId);
        user.changePassword(currentPw, newPw, passwordEncoder);
        userRepository.save(user);
    }
}

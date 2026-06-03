package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserEntity signup(String userId, String rawPassword, String name, LocalDate birthDate, String email) {
        PasswordVO.validatePolicy(rawPassword, birthDate);
        if (userRepository.findByUserId(userId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }
        PasswordVO passwordVO = PasswordVO.fromEncoded(passwordEncoder.encode(rawPassword));
        return userRepository.save(new UserEntity(userId, passwordVO, name, birthDate, email));
    }

    @Transactional(readOnly = true)
    public UserEntity getUser(String userId, String rawPassword) {
        UserEntity user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    @Transactional
    public UserEntity changePassword(String userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        PasswordVO.validatePolicy(newPassword, user.getBirthDate());
        user.changePassword(PasswordVO.fromEncoded(passwordEncoder.encode(newPassword)));
        return userRepository.save(user);
    }
}

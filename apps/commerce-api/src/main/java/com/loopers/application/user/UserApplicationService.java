package com.loopers.application.user;

import com.loopers.domain.user.PasswordVO;
import com.loopers.domain.user.UserEntity;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class UserApplicationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserInfo signup(String userId, String rawPassword, String name, LocalDate birthDate, String email) {
        PasswordVO.validatePolicy(rawPassword, birthDate);
        if (userRepository.findByUserId(userId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }
        PasswordVO passwordVO = PasswordVO.fromEncoded(passwordEncoder.encode(rawPassword));
        UserEntity saved = userRepository.save(new UserEntity(userId, passwordVO, name, birthDate, email));
        return UserInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public UserInfo getUser(String userId, String rawPassword) {
        UserEntity user = findByUserIdOrThrow(userId);
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        UserEntity user = findByUserIdOrThrow(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        PasswordVO.validatePolicy(newPassword, user.getBirthDate());
        user.changePassword(PasswordVO.fromEncoded(passwordEncoder.encode(newPassword)));
        userRepository.save(user);
    }

    public Long authenticate(String loginId, String password) {
        try {
            UserEntity user = findByUserIdOrThrow(loginId);
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
            }
            return user.getId();
        } catch (CoreException e) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    private UserEntity findByUserIdOrThrow(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
    }
}

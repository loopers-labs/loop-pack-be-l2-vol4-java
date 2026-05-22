package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signUp(UserModel user) {
        user.encodePassword(passwordEncoder);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + user.getLoginId() + "] 이미 존재하는 아이디입니다.");
        }
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String loginId, String rawPassword) {
        UserModel user = findUserOrThrow(loginId);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    @Transactional(readOnly = true)
    public UserModel getUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[userId = " + userId + "] 유저를 찾을 수 없습니다."));
    }

    @Transactional
    public void updatePassword(String loginId, String oldRawPassword, String newRawPassword) {
        UserModel user = findUserOrThrow(loginId);

        if (!passwordEncoder.matches(oldRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 다르게 설정해야 합니다.");
        }
        UserModel.validatePassword(newRawPassword, user.getBirthDate());
        user.updatePassword(passwordEncoder.encode(newRawPassword));
    }

    private UserModel findUserOrThrow(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 유저를 찾을 수 없습니다."));
    }
}

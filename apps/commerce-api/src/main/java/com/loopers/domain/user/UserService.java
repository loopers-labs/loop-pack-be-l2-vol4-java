package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserModel signUp(UserModel user) {
        if (userRepository.existsByLoginId(user.getLoginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<UserModel> getMyInfo(String loginId) {
        return userRepository.findByLoginId(loginId);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        user.changePassword(currentPassword, newPassword);
    }
}

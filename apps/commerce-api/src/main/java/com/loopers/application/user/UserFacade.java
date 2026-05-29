package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserRepository userRepository;

    @Transactional
    public UserInfo signUp(SignUpCommand command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        UserModel saved = userRepository.save(command.toModel());
        return UserInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<UserInfo> getMyInfo(String loginId, String loginPw) {
        return userRepository.findByLoginId(loginId)
            .filter(user -> user.matchesPassword(loginPw))
            .map(UserInfo::from);
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        user.changePassword(currentPassword, newPassword);
        userRepository.save(user);
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserInfo signUp(UserModel user) {
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public UserInfo getUser(String loginId, String password) {
        UserModel user = userService.getUser(loginId, password);
        return UserInfo.from(user);
    }

    public UserInfo getUser(Long userId) {
        UserModel user = userService.getUserById(userId);
        return UserInfo.from(user);
    }

    public void updatePassword(String loginId, String oldPassword, String newPassword) {
        userService.updatePassword(loginId, oldPassword, newPassword);
    }

    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        UserModel user = userService.getUserById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 일치하지 않습니다.");
        }
        userService.updatePassword(user.getLoginId(), oldPassword, newPassword);
    }
}

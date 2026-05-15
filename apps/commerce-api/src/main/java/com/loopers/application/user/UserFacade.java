package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(UserModel user) {
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public UserInfo getUser(String loginId, String password) {
        UserModel user = userService.getUser(loginId, password);
        return UserInfo.from(user);
    }

    public void updatePassword(String loginId, String oldPassword, String newPassword) {
        userService.updatePassword(loginId, oldPassword, newPassword);
    }
}

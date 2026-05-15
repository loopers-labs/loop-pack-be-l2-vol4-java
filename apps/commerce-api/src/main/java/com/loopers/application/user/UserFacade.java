package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(UserRegisterCommand command) {
        UserModel user = userService.register(command);
        return UserInfo.from(user);
    }

    public UserInfo getMe(String loginId) {
        UserModel user = userService.getUser(loginId);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        userService.changePassword(loginId, currentPassword, newPassword);
    }

    public String authenticate(String loginId, String rawPassword) {
        return userService.authenticate(loginId, rawPassword).getLoginId();
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegistrationCommand;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public void register(UserRegistrationCommand command) {
        userService.register(command);
    }

    public UserInfo getMe(UserModel authenticatedUser) {
        return UserInfo.from(authenticatedUser);
    }

    public void changePassword(UserModel authenticatedUser, String currentPassword, String newPassword) {
        userService.changePassword(authenticatedUser.getLoginId(), currentPassword, newPassword);
    }
}

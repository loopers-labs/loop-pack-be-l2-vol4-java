package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public void signUp(UserCommand.SignUp command) {
        userService.signUp(
            command.loginId(),
            command.password(),
            command.name(),
            command.birthDate(),
            command.email()
        );
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userService.getById(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }
}

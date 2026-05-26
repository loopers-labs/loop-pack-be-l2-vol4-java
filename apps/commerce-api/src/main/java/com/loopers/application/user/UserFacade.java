package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo join(UserCommand.Join command) {
        User user = userService.join(
                command.loginId(),
                command.loginPassword(),
                command.name(),
                command.birthday(),
                command.email()
        );
        return UserInfo.from(user);
    }

    public UserInfo getUser(User user) {
        return UserInfo.fromWithMaskedName(user);
    }

    public void changePassword(User user, UserCommand.ChangePassword command) {
        userService.changePassword(user, command.currentPassword(), command.newPassword());
    }
}

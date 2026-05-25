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

    public UserInfo getUser(UserCommand.GetUser command) {
        return UserInfo.fromWithMaskedName(userService.getUser(command.loginId(), command.loginPassword()));
    }

    public void changePassword(UserCommand.ChangePassword command) {
        User user = userService.getUser(command.loginId(), command.loginPassword());
        userService.changePassword(user, command.currentPassword(), command.newPassword());
    }
}

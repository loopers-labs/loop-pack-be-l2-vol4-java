package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.command.SignUpUserCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
import com.loopers.domain.user.vo.UserName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpRequestCommand command) {
        BirthDate birthDate = BirthDate.of(command.birthDate());
        SignUpUserCommand signUpUserCommand = new SignUpUserCommand(
            LoginId.of(command.loginId()),
            PlainPassword.of(command.password(), birthDate),
            UserName.of(command.name()),
            birthDate,
            Email.of(command.email())
        );
        User user = userService.signUp(signUpUserCommand);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(Long userId) {
        User user = userService.getUser(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }
}

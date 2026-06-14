package com.loopers.user.application;

import com.loopers.user.domain.User;
import com.loopers.user.domain.UserService;
import com.loopers.user.domain.command.SignUpUserCommand;
import com.loopers.user.domain.vo.BirthDate;
import com.loopers.user.domain.vo.Email;
import com.loopers.user.domain.vo.LoginId;
import com.loopers.user.domain.vo.PlainPassword;
import com.loopers.user.domain.vo.UserName;
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

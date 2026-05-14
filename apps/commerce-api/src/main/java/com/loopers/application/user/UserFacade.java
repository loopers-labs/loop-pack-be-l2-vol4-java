package com.loopers.application.user;

import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Password;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo.User signUp(UserCommand.SignUp command) {
        UserModel user = userService.signUp(
            LoginId.of(command.loginId()),
            Password.of(command.password()),
            command.name(),
            BirthDate.of(command.birthDate()),
            Email.of(command.email())
        );
        return UserInfo.User.from(user);
    }
}

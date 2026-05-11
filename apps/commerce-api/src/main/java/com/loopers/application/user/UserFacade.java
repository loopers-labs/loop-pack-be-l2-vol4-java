package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpCommand command) {
        UserModel user = userService.signUp(
            command.loginId(),
            command.password(),
            command.name(),
            command.birthDate(),
            command.email()
        );
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userService.getById(userId);
        return UserInfo.from(user);
    }
}
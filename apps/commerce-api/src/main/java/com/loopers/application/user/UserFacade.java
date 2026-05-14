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
        UserModel user = command.toModel();
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }
}

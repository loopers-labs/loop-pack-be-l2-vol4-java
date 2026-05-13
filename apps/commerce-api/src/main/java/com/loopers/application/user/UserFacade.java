package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String password, String name, String birthDate, String email) {
        UserModel user = userService.signUp(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo authenticate(String loginId, String rawPassword) {
        UserModel user = userService.authenticate(loginId, rawPassword);
        return UserInfo.from(user);
    }
}

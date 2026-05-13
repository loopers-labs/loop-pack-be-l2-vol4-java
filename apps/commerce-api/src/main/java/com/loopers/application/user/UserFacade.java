package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(String loginId, String password, String name, String birth, String email) {
        UserModel userModel = new UserModel(loginId, password, name, birth, email);
        UserModel saved = userService.register(userModel);
        return UserInfo.from(saved);
    }
}

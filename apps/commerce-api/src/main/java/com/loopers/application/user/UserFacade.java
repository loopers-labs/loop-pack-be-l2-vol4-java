package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo createUser(String loginId, String loginPw) {
        UserModel user = userService.createUser(loginId, loginPw);
        return UserInfo.from(user);
    }
}

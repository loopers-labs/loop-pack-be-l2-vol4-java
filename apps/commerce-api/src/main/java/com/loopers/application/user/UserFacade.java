package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo registerUser(String userid, String password, String name, String birthDay, String email) {
        UserModel user = userService.register(userid, password, name, birthDay, email);
        return UserInfo.from(user);
    }

    public UserInfo getUser(String userid) {
        UserModel user = userService.getUser(userid);
        return UserInfo.from(user);
    }

    public UserInfo changePassword(String userid, String newPassword) {
        UserModel user = userService.changePassword(userid, newPassword);
        return UserInfo.from(user);
    }

}

package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signup(String loginId, String password, String name, LocalDate birth, String email) {
        UserModel user = userService.signup(loginId, password, name, birth, email);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId) {
        UserModel user = userService.getByLoginId(loginId);
        return UserInfo.fromMasked(user);
    }

    public void changePassword(String loginId, String oldPassword, String newPassword) {
        userService.changePassword(loginId, oldPassword, newPassword);
    }
}

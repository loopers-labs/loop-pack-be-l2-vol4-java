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

    public UserInfo registerUser(String userId, String password, String name, LocalDate birthDate, String email) {
        UserModel user = userService.registerUser(userId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getUser(String userId, String password) {
        UserModel user = userService.getUser(userId, password);
        return UserInfo.from(user);
    }

    public UserInfo changePassword(String userId, String currentPassword, String newPassword) {
        UserModel user = userService.changePassword(userId, currentPassword, newPassword);
        return UserInfo.from(user);
    }

}

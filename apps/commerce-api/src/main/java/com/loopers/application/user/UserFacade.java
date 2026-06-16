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

    public void signup(String userId, String password, String name, LocalDate birthDate, String email) {
        userService.signup(userId, password, name, birthDate, email);
    }

    public UserInfo getUser(String userId, String password) {
        UserModel user = userService.getUser(userId, password);
        return UserInfo.from(user);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }
}

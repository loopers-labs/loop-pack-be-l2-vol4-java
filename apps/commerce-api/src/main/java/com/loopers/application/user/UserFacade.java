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

    public UserInfo signUp(String loginId, String password, String name, LocalDate birthDate, String email) {
        UserModel user = new UserModel(loginId, password, name, birthDate, email);
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public UserInfo getUser(String loginId, String password) {
        UserModel user = userService.getUser(loginId, password);
        return UserInfo.from(user);
    }

    public UserInfo getUser(Long userId) {
        UserModel user = userService.getUserById(userId);
        return UserInfo.from(user);
    }

    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        userService.updatePassword(userId, oldPassword, newPassword);
    }

    public void withdraw(Long userId) {
        userService.withdraw(userId);
    }
}

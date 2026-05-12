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

    public void signUp(String loginId, String password, String name, LocalDate birthDate, String email) {
        userService.signUp(loginId, password, name, birthDate, email);
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userService.getById(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }
}

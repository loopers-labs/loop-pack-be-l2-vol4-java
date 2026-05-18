package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UserFacade {
    private final UserService userService;

    public UserInfo signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthday,
            String email
    ) {
        UserModel saved = userService.signUp(
                new UserModel(loginId, password, name, birthday, email)
        );
        return toUserInfo(saved);
    }

    public UserInfo getMyInfo(String loginId, String rawPassword) {
        UserModel user = userService.authenticate(loginId, rawPassword);
        return toUserInfo(user);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel user = userService.authenticate(loginId, currentPassword);
        userService.changePassword(user.getId(), currentPassword, newPassword);
    }

    private static UserInfo toUserInfo(UserModel user) {
        return new UserInfo(
                user.getLoginId().getValue(),
                maskLastChar(user.getName()),
                user.getBirthday(),
                user.getEmail()
        );
    }

    private static String maskLastChar(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return "*";
        return name.substring(0, name.length() - 1) + "*";
    }
}

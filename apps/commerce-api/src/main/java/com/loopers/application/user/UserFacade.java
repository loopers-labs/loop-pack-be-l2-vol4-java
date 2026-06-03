package com.loopers.application.user;

import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UserFacade {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserInfo signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthday,
            String email
    ) {
        UserModel saved = userService.signUp(
                new UserModel(loginId, password, name, birthday, email, passwordEncoder)
        );
        return toUserInfo(saved);
    }

    public UserInfo getMyInfo(String loginId, String rawPassword) {
        UserModel user = userService.authenticate(loginId, rawPassword);
        return toUserInfo(user);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        userService.changePassword(loginId, currentPassword, newPassword);
    }

    /**
     * 인증 후 userId 반환 — Like/Order 등 사용자 식별이 필요한 진입점에서 사용.
     * 자격 증명이 틀리면 UserService.authenticate가 UNAUTHORIZED를 던진다.
     */
    public Long authenticate(String loginId, String rawPassword) {
        return userService.authenticate(loginId, rawPassword).getId();
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

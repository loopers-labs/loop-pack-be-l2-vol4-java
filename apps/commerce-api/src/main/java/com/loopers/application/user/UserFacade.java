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

    public UserInfo signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        UserModel user = userService.register(loginId, rawPassword, name, birthDate, email);
        return UserInfo.ofRegister(user);
    }

    public UserInfo getUser(String loginId, String rawPassword) {
        UserModel user = userService.getUser(loginId, rawPassword);
        return UserInfo.from(user);
    }

    public void updatePassword(String loginId, String rawCurrentPassword, String rawNewPassword) {
        userService.changePassword(loginId, rawCurrentPassword, rawNewPassword);
    }
}

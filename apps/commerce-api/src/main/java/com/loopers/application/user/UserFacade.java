package com.loopers.application.user;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserSignUpInfo signUp(String rawLoginId, String rawPassword, String rawName, LocalDate rawBirthDate, String rawEmail) {
        UserModel newUser = userService.signUp(rawLoginId, rawPassword, rawName, rawBirthDate, rawEmail);

        return UserSignUpInfo.from(newUser);
    }

    public UserMyInfo getMyInfo(UserModel authenticatedUser) {
        return UserMyInfo.from(authenticatedUser);
    }
}

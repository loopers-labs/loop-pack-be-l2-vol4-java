package com.loopers.application.user;

import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public void signUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        userService.signUp(
                loginId,
                password,
                name,
                birthDate,
                email
        );
    }

    public UserInfo getMyInfo(String loginId, String password) {
        return userService.getUser(loginId, password);
    }

    public void updatePassword(
            String loginId,
            String password,
            String oldPassword,
            String newPassword
    ) {
        userService.updatePassword(
                loginId,
                password,
                oldPassword,
                newPassword
        );
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    @Transactional
    public UserInfo createUser(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        UserModel userModel = userService.createUser(
                                      loginId,
                                      password,
                                      name,
                                      birthDate,
                                      email
                              );

        return UserInfo.from(userModel);
    }


    public UserInfo getMyInfo(String loginId) {
        UserModel userModel = userService.getMyInfo(loginId);
        return UserInfo.from(userModel);
    }

    @Transactional
    public UserInfo changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel userModel = userService.changePassword(loginId, currentPassword, newPassword);
        return UserInfo.from(userModel);
    }
}

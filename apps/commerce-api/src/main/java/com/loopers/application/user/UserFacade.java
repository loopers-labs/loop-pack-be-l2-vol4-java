package com.loopers.application.user;

import com.loopers.domain.user.service.UserAuthService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.service.UserPasswordService;
import com.loopers.domain.user.service.UserSignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserSignupService userSignupService;
    private final UserAuthService userAuthService;
    private final UserPasswordService userPasswordService;

    public void signUp(String loginId, String password, String name, LocalDate birthDate, String email) {
        userSignupService.signup(loginId, password, name, birthDate, email);
    }

    public Long authenticate(String loginId, String rawPassword) {
        UserModel user = userAuthService.authenticate(loginId, rawPassword);
        return user.getId();
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userAuthService.getById(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userPasswordService.changePassword(userId, currentPassword, newPassword);
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final CreateUserService createUserService;
    private final UpdateUserService updateUserService;
    private final FindUserService findUserService;

    public UserInfo signUp(String loginId, String password, String name, String birthDate, String email, Gender gender) {
        UserModel user = createUserService.signUp(loginId, password, name, birthDate, email, gender);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId, String loginPw) {
        UserModel user = findUserService.getLoginUser(loginId, loginPw);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String loginPw, String oldPassword, String newPassword) {
        updateUserService.changePassword(loginId, loginPw, oldPassword, newPassword);
    }

}

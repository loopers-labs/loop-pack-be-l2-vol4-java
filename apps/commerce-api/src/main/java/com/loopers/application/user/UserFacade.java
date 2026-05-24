package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserCreator userCreator;
    private final UserModifier userModifier;
    private final UserFinder userFinder;

    public UserInfo signUp(String loginId, String password, String name, String birthDate, String email, Gender gender) {
        UserModel user = userCreator.create(loginId, password, name, birthDate, email, gender);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId, String loginPw) {
        UserModel user = userFinder.getLoginUser(loginId, loginPw);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String loginPw, String oldPassword, String newPassword) {
        userModifier.changePassword(loginId, loginPw, oldPassword, newPassword);
    }

}

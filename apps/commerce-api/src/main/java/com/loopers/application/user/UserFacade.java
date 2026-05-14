package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(String loginId, String password, String name, String birth, String email) {
        UserModel userModel = new UserModel(loginId, password, name, birth, email);
        UserModel saved = userService.register(userModel);
        return UserInfo.from(saved);
    }

    /** 내 정보 조회 — 먼저 인증 후, 인증된 유저 정보를 반환한다. */
    public UserInfo getMyInfo(String loginId, String password) {
        UserModel user = userService.authenticate(loginId, password);
        return UserInfo.from(user);
    }

    /** 비밀번호 변경 — 먼저 인증 후, 새 비밀번호로 변경한다. */
    public void changePassword(String loginId, String password, String newPassword) {
        userService.authenticate(loginId, password);
        userService.changePassword(loginId, newPassword);
    }
}

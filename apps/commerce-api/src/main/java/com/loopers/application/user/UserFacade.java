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

    /** 내 정보 조회 — 인터셉터에서 인증된 유저 정보를 반환한다. */
    public UserInfo getMyInfo(UserModel user) {
        return UserInfo.from(user);
    }

    /** 비밀번호 변경 — 인터셉터에서 인증된 유저의 비밀번호를 변경한다. */
    public void changePassword(UserModel user, String newPassword) {
        userService.changePassword(user.getLoginId(), newPassword);
    }
}

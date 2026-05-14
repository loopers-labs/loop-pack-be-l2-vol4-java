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

    /** 내 정보 조회 — 인증은 Service 에서 처리, 마스킹된 정보를 반환한다. */
    public UserInfo getMyInfo(String loginId, String password) {
        UserModel user = userService.getMyInfo(loginId, password);
        return UserInfo.from(user);
    }

    /** 비밀번호 변경 — 기존 비밀번호 불일치 시 UNAUTHORIZED, 새 비밀번호 RULE 위반 시 BAD_REQUEST. */
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        userService.changePassword(loginId, currentPassword, newPassword);
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(String loginId, String loginPw, String email, String nickname) {
        return UserInfo.from(userService.register(loginId, loginPw, email, nickname));
    }

    public UserInfo login(String loginId, String loginPw) {
        return UserInfo.from(userService.login(loginId, loginPw));
    }

    public UserInfo getMe(String loginId) {
        return UserInfo.from(userService.getUserByLoginId(loginId));
    }
}

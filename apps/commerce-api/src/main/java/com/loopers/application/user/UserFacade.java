package com.loopers.application.user;

import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    @Transactional
    public UserInfo signup(String loginId, String password, String name, LocalDate birth, String email) {
        return UserInfo.from(userService.register(loginId, password, name, birth, email));
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        return UserInfo.fromMasked(userService.getMyInfo(loginId));
    }
}

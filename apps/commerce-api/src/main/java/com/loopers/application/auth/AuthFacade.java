package com.loopers.application.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class AuthFacade {

    private final UserService userService;

    @Transactional(readOnly = true)
    public AuthenticatedUserInfo authenticate(String loginId, String password) {
        UserModel user = userService.authenticate(loginId, password);
        return AuthenticatedUserInfo.from(user);
    }
}

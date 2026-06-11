package com.loopers.application.auth;

import com.loopers.domain.user.User;
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
        User user = userService.authenticate(loginId, password);
        return AuthenticatedUserInfo.from(user);
    }
}

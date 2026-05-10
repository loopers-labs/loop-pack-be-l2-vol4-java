package com.loopers.application.user;

import com.loopers.domain.user.Birth;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(LoginId loginId, Name name, Birth birth, Email email, String rawPassword) {
        User user = userService.register(loginId, name, birth, email, rawPassword);
        return UserInfo.from(user);
    }
}

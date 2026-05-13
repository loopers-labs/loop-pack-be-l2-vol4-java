package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(
        String loginId,
        String password,
        String name,
        LocalDate birth,
        String email
    ) {
        User user = userService.register(loginId, password, name, birth, email);
        return UserInfo.from(user);
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String password, String name, String birthDate, String email, Gender gender) {
        UserModel user = userService.signUp(loginId, password, name, birthDate, email, gender);
        return UserInfo.from(user);
    }
}

package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo getUser(Long id) {
        User user = userService.getUser(id);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPw, String newPw) {
        userService.changePassword(userId, currentPw, newPw);
    }
}

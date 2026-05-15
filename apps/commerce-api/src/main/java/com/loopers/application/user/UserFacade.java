package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpCommand command) {
        UserModel user = command.toModel();
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public Optional<UserInfo> getMyInfo(String loginId, String loginPw) {
        return userService.getMyInfo(loginId)
            .filter(user -> user.matchesPassword(loginPw))
            .map(UserInfo::from);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        userService.changePassword(loginId, currentPassword, newPassword);
    }
}

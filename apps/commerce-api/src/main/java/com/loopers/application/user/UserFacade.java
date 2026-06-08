package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.user.AuthUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(UserRegisterCommand command) {
        UserModel user = userService.register(command);
        return UserInfo.from(user);
    }

    public UserInfo getMe(Long userId) {
        UserModel user = userService.getUserById(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }

    /**
     * 인증과 동시에 userId까지 담아 AuthUserContext를 반환한다.
     * Facade/Service에서 별도로 UserService.getUser()를 재호출할 필요가 없다.
     */
    public AuthUserContext authenticate(String loginId, String rawPassword) {
        UserModel user = userService.authenticate(loginId, rawPassword);
        return new AuthUserContext(user.getLoginId(), user.getId());
    }
}

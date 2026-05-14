package com.loopers.application.user;

import com.loopers.domain.user.Birth;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
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
        User user = userService.register(
            new LoginId(loginId),
            password,
            new Name(name),
            new Birth(birth),
            new Email(email)
        );
        return UserInfo.from(user);
    }

    public UserInfo findMyInfo(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.maskedName(),
            user.getBirth(),
            user.getEmail()
        );
    }

    public void changePassword(User user, String currentPassword, String newPassword) {
        userService.changePassword(new LoginId(user.getLoginId()), currentPassword, newPassword);
    }
}

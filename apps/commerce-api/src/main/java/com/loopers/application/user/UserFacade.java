package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo registerUser(String userid, String password, String name, String birthDay, String email) {
        UserModel user = userService.register(
                new UserId(userid),
                new RawPassword(password),
                new Name(name),
                new BirthDay(birthDay),
                new Email(email)
        );
        return UserInfo.from(user);
    }

    public UserInfo getUser(String userid) {
        UserModel user = userService.getUser(new UserId(userid));
        return UserInfo.from(user);
    }

    public UserInfo changePassword(String userid, String newPassword) {
        UserModel user = userService.changePassword(new UserId(userid), new RawPassword(newPassword));
        return UserInfo.from(user);
    }
}

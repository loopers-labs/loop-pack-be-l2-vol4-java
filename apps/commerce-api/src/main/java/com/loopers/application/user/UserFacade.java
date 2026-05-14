package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

    public UserInfo getMyInfo(String loginId, String loginPw) {
        UserModel user = userService.findMyInfo(loginId, loginPw);
        if (user == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 회원을 찾을 수 없습니다.");
        }
        return UserInfo.from(user);
    }
}

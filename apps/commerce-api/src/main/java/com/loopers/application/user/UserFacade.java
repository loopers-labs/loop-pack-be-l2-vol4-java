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

    public UserInfo signUp(String loginId, String password, String name, String email, String birthDate, Gender gender) {
        UserModel user = new UserModel(loginId, password, name, email, birthDate, gender);
        UserModel saved = userService.signUp(user);
        return UserInfo.from(saved);
    }

    public UserInfo getMyInfo(String loginId) {
        return userService.findByLoginId(loginId)
            .map(UserInfo::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}

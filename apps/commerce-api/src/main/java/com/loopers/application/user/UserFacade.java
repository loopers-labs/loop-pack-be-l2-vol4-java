package com.loopers.application.user;

import com.loopers.domain.user.UserEntity;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public void signup(String userId, String password, String name, LocalDate birthDate, String email) {
        userService.signup(userId, password, name, birthDate, email);
    }

    public UserInfo getUser(String userId, String password) {
        UserEntity user = userService.getUser(userId, password);
        return UserInfo.from(user);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        userService.changePassword(userId, currentPassword, newPassword);
    }

    public Long authenticate(String loginId, String password) {
        try {
            UserEntity user = userService.getUser(loginId, password);
            return user.getId();
        } catch (CoreException e) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }
}

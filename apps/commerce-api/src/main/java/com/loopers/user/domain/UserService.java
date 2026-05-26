package com.loopers.user.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final PasswordEncryptor passwordEncryptor;

    public UserModel getOrThrow(Optional<UserModel> user) {
        return user.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    public UserModel signUp(Optional<UserModel> existing, UserModel newUser) {
        existing.ifPresent(e -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 ID입니다.");
        });
        newUser.encodePassword(passwordEncryptor);
        return newUser;
    }

    public void changePassword(UserModel user, String newPassword) {
        if (passwordEncryptor.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.CONFLICT, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        user.changePassword(newPassword);
        user.encodePassword(passwordEncryptor);
    }
}

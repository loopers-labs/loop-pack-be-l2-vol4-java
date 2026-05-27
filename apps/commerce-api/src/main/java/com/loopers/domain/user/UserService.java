package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;

    public UserModel signup(
        String loginId,
        String password,
        String name,
        LocalDate birth,
        String email,
        boolean loginIdExists
    ) {
        if (loginIdExists) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + loginId + "] 이미 가입된 로그인 ID입니다.");
        }

        passwordPolicy.validate(password, birth);
        String passwordHash = passwordHasher.encode(password);
        return new UserModel(loginId, passwordHash, name, birth, email);
    }

    public void validateCredential(String loginId, String password) {
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }
    }

    public void authenticate(UserModel user, String password) {
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.");
        }
    }

    public void changePassword(UserModel user, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        passwordPolicy.validate(newPassword, user.getBirth());
        if (passwordHasher.matches(newPassword, user.getPasswordHash())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        user.changePasswordHash(passwordHasher.encode(newPassword));
    }
}

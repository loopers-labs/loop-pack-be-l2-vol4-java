package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

public class UserEntity extends BaseEntity {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private String userId;
    private String name;
    private String email;
    private PasswordVO password;
    private LocalDate birthDate;

    protected UserEntity() {}

    public UserEntity(String userId, PasswordVO password, String name, LocalDate birthDate, String email) {
        validateUserId(userId);
        validateName(name);
        validateEmail(email);

        this.userId = userId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public static UserEntity of(String id, String userId, String name, String email, String encodedPassword,
            LocalDate birthDate, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        UserEntity model = new UserEntity();
        model.userId = userId;
        model.name = name;
        model.email = email;
        model.password = PasswordVO.fromEncoded(encodedPassword);
        model.birthDate = birthDate;
        model.reconstruct(id, createdAt, updatedAt, deletedAt);
        return model;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password.value();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    public void changePassword(PasswordVO newPassword) {
        this.password = newPassword;
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 허용되며 특수문자와 공백을 포함할 수 없습니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }
}

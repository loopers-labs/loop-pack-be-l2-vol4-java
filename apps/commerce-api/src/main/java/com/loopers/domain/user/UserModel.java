package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    @Builder
    public UserModel(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateEmail(email);
        validateName(name);
        validateEncodedPassword(encodedPassword);
        validateBirthDate(birthDate);

        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 비어있을 수 없습니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    private static void validateEncodedPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
    }

}

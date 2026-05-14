package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserModel extends BaseEntity {

    private String loginId;

    @Embedded
    private Password password;

    private String name;
    private String birthDate;
    private String email;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]{1,10}$";
    private static final String EMAIL_PATTERN = "^[^@]+@[^@]+\\.[^@]+$";
    private static final String BIRTH_DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";

    protected UserModel() {}

    public UserModel(String loginId, String rawPassword, String name, String birthDate, String email, Gender gender, PasswordHasher hasher) {
        if (loginId == null || !loginId.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문/숫자 1~10자여야 합니다.");
        }
        if (email == null || !email.matches(EMAIL_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
        if (birthDate == null || !birthDate.matches(BIRTH_DATE_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        Password validated = Password.of(rawPassword, hasher);
        String birthDateDigits = birthDate.replace("-", "");
        if (rawPassword.contains(birthDateDigits)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        this.loginId = loginId;
        this.password = validated;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.gender = gender;
    }
}

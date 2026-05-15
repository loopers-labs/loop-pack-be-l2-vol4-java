package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$");

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false)
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용 가능합니다.");
        }
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (!birthDate.isBefore(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 현재 날짜 이전이어야 합니다.");
        }
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public String maskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    public void changePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }
}

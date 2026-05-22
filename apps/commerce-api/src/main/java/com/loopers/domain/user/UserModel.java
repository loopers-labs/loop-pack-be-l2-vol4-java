package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final String MASK = "*";
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER_8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER_6 = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER_4 = DateTimeFormatter.ofPattern("MMdd");

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateBirthDate(birthDate);
        validatePassword(password, birthDate);
        validateName(name);
        validateEmail(email);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public void encodePassword(PasswordEncoder encoder) {
        this.password = encoder.encode(this.password);
    }

    public void updatePassword(String encodedNewPassword) {
        this.password = encodedNewPassword;
    }

    public String getPassword() {
        return password;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
    }

    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + MASK;
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.");
        }
        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 영문과 숫자만 사용 가능합니다.");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
    }

    public static void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
        if (!password.matches("^[\\x21-\\x7E]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
        if (password.contains(birthDate.format(BIRTH_DATE_FORMATTER_8))
                || password.contains(birthDate.format(BIRTH_DATE_FORMATTER_6))
                || password.contains(birthDate.format(BIRTH_DATE_FORMATTER_4))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }
}

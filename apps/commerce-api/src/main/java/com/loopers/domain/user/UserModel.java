package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "user")
public class UserModel extends BaseEntity {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String userId;
    private String name;
    private String email;
    private String password;
    private LocalDate birthDate;

    protected UserModel() {}

    public UserModel(String userId, String password, String name, LocalDate birthDate, String email) {
        validateUserId(userId);
        validateName(name);
        validateEmail(email);
        validatePassword(password, birthDate);

        this.userId = userId;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
        this.password = PASSWORD_ENCODER.encode(password);
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
        return password;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!userId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!name.matches("^[가-힣]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 허용되며 특수문자와 공백을 포함할 수 없습니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (password.length() < 8 || password.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
        validatePasswordNotContainsBirthDate(password, birthDate);
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String fullDate = birthDate.format(BIRTH_DATE_FORMAT);  // 19950610
        String yearlessDate = fullDate.substring(2);             // 950610
        String monthDay = fullDate.substring(4);                 // 0610

        if (password.contains(fullDate) || password.contains(yearlessDate) || password.contains(monthDay)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}

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

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{8,16}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣]{1,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Column(name = "login_id", nullable = false, unique = true, length = 16)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String encodedPassword;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "email", nullable = false)
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, String encodedPassword, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateEmail(email);
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }

        this.loginId = loginId;
        this.encodedPassword = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public String maskedName() {
        if (name.length() == 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }

    public void changePassword(String newEncodedPassword) {
        if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        this.encodedPassword = newEncodedPassword;
    }

    public static void validateRawPassword(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자로 구성된 8~16자여야 합니다.");
        }
        if (containsBirthDate(rawPassword, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자로 구성된 8~16자여야 합니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글/영문으로 구성된 1~20자여야 합니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    private static boolean containsBirthDate(String password, LocalDate birthDate) {
        String year = String.valueOf(birthDate.getYear());
        String yearShort = year.substring(2);
        String month = String.format("%02d", birthDate.getMonthValue());
        String day = String.format("%02d", birthDate.getDayOfMonth());
        return password.contains(year)
            || password.contains(yearShort)
            || password.contains(month)
            || password.contains(day);
    }
}

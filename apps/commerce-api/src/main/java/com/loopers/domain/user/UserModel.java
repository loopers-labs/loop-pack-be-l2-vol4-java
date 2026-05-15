package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern BIRTH_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 16;
    private static final Pattern PASSWORD_ALLOWED_CHARS =
        Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~]+$");
    private static final Pattern PASSWORD_HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASSWORD_HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern PASSWORD_HAS_SPECIAL =
        Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~].*");

    @Column(name = "login_id", nullable = false, unique = true, length = 10)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false, length = 10)
    private String birthDate;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String gender;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, String birthDate, String email, String gender) {
        validateLoginId(loginId);
        validateName(name);
        validateBirthDate(birthDate);
        validateEmail(email);
        validateGender(gender);
        validatePassword(password, birthDate);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.gender = gender;
    }

    public boolean matchesPassword(String password) {
        return this.password.equals(password);
    }

    public String getMaskedName() {
        if (this.name.length() == 1) {
            return "*";
        }
        return this.name.substring(0, this.name.length() - 1) + "*";
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (!matchesPassword(currentPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        validatePassword(newPassword, this.birthDate);
        if (newPassword.equals(this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 다른 값이어야 합니다.");
        }
        this.password = newPassword;
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문/숫자 10자 이내여야 합니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    private static void validateBirthDate(String birthDate) {
        if (birthDate == null || !BIRTH_DATE_PATTERN.matcher(birthDate).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
        try {
            LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일이 유효한 날짜가 아닙니다.");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 xx@yy.zz 형식이어야 합니다.");
        }
    }

    private static void validateGender(String gender) {
        if (gender == null || gender.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 필수 입력값입니다.");
        }
    }

    private static void validatePassword(String password, String birthDate) {
        if (password == null
            || password.length() < PASSWORD_MIN_LENGTH
            || password.length() > PASSWORD_MAX_LENGTH
            || !PASSWORD_ALLOWED_CHARS.matcher(password).matches()
            || !PASSWORD_HAS_LETTER.matcher(password).matches()
            || !PASSWORD_HAS_DIGIT.matcher(password).matches()
            || !PASSWORD_HAS_SPECIAL.matcher(password).matches()
        ) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자 영문/숫자/특수문자 조합이어야 합니다.");
        }
        if (birthDate != null) {
            String birthDateNoSep = birthDate.replace("-", "");
            if (password.contains(birthDate) || password.contains(birthDateNoSep)) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다.");
            }
        }
    }
}

package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final Pattern VALID_LOGIN_ID = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern VALID_PASSWORD = Pattern.compile("^[a-zA-Z0-9\\p{Punct}]{8,16}$");
    private static final Pattern VALID_NAME = Pattern.compile("^[가-힣a-zA-Z]+$");
    private static final Pattern VALID_EMAIL = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final DateTimeFormatter BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd")
        .withResolverStyle(ResolverStyle.STRICT);

    private String loginId;
    private String password;
    private String name;
    private String birthDate;
    private String email;

    public UserModel(UserRegistrationCommand command) {
        String loginId = command.loginId();
        String password = command.password();
        String name = command.name();
        String birthDate = command.birthDate();
        String email = command.email();

        if (loginId == null || !VALID_LOGIN_ID.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용 가능합니다.");
        }
        if (password == null || !VALID_PASSWORD.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 사용 가능합니다.");
        }
        if (email == null || !VALID_EMAIL.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
        if (!isValidBirthDate(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyyMMdd 형식이어야 합니다.");
        }
        if (password.contains(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    private static boolean isValidBirthDate(String birthDate) {
        if (birthDate == null) {
            return false;
        }
        try {
            LocalDate.parse(birthDate, BIRTH_DATE_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public void encodePassword(PasswordEncoder encoder) {
        this.password = encoder.encode(this.password);
    }

    public void changePassword(String currentPassword, String newPassword, PasswordEncoder encoder) {
        if (!encoder.matches(currentPassword, this.password)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (newPassword == null || !VALID_PASSWORD.matcher(newPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
        if (newPassword.contains(this.birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        if (encoder.matches(newPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }
        this.password = encoder.encode(newPassword);
    }

}

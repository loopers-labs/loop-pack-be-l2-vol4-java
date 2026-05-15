package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Entity
@Table(name = "user")
public class UserModel extends BaseEntity {
    private static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9]+$";
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final int PW_LETTER_MAX_SIZE = 16;
    private static final int PW_LETTER_MIN_SIZE = 8;
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/~`|\\\\]+$";

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, String birthDate, String email) {
        if (!loginId.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 id는 영문과 숫자만 허용됩니다.");
        }

        LocalDate parsedBirthDate;
        try {
            parsedBirthDate = LocalDate.parse(birthDate);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일의 구조는 yyyy-MM-dd 에 맞춰서 넣어주시기 바랍니다.");
        }

        if (password.length() > PW_LETTER_MAX_SIZE || password.length() < PW_LETTER_MIN_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자 이내로 작성해주시기 바랍니다.");
        }
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자로만 구성됩니다.");
        }
        if (passwordContainsBirthDate(password, parsedBirthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일 정보가 포함될 수 없습니다.");
        }

        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름을 적어주시기 바랍니다.");
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일을 형태에 알맞게 작성해주시기 바랍니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = parsedBirthDate;
        this.email = email;
    }

    private boolean passwordContainsBirthDate(String password, LocalDate birthDate) {
        String dashed   = birthDate.toString();                                       // "1998-04-11"
        String compact  = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);         // "19980411"
        String yearOnly = String.valueOf(birthDate.getYear());                        // "1998"
        String monthDay = birthDate.format(DateTimeFormatter.ofPattern("MMdd"));      // "0411"

        return password.contains(dashed)
                || password.contains(compact)
                || password.contains(yearOnly)
                || password.contains(monthDay);
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
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

    public void encodePassword(PasswordEncoder encoder){
        this.password = encoder.encode(this.password);
    }
}

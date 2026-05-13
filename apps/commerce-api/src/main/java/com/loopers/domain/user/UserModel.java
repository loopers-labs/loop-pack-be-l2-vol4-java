package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.infrastructure.user.EncodedPasswordConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern BIRTH_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Convert(converter = EncodedPasswordConverter.class)
    @Column(name = "password", nullable = false)
    private EncodedPassword password;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private String birthDate;

    @Column(nullable = false)
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, EncodedPassword password, String name, String birthDate, String email) {
        if (loginId == null || loginId.isBlank() || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 형식이 올바르지 않습니다.");
        }
        if (password == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (birthDate == null || birthDate.isBlank() || !BIRTH_DATE_PATTERN.matcher(birthDate).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다.");
        }
        try {
            LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 날짜입니다.");
        }
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }
}

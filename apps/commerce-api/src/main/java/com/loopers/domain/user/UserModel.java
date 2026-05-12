package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private String loginId;
    private String password;
    private String name;
    private String email;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, String email, String birthDate, Gender gender) {
        if (loginId == null || !loginId.matches("^[a-zA-Z0-9]{1,10}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "loginId는 영문 및 숫자 10자 이내여야 합니다.");
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        try {
            this.birthDate = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
        if (!this.birthDate.isBefore(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 오늘 이전이어야 합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        String birthDateNumeric = this.birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (password == null || !password.matches("^[\\x21-\\x7E]{8,16}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (password.contains(birthDateNumeric)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.gender = gender;
    }

    public void encodePassword(PasswordEncryptor encryptor) {
        this.password = encryptor.encrypt(this.password);
    }

    public String getLoginId() { return loginId; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public LocalDate getBirthDate() { return birthDate; }
    public Gender getGender() { return gender; }
}

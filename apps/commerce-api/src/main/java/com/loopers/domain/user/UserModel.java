package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.regex.Pattern;

@Entity
@Table(name = "user")
public class UserModel extends BaseEntity {

    private String userid;
    private String password;
    private String name;
    private String birthDay;
    private String email;

    private UserModel() {}

    private static final Pattern USERID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]+$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=]{8,16}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            );

    private static final Pattern BIRTHDAY_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    public static void validatePassword(String rawPassword, String birthDay) {
        if (!PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (rawPassword.contains(birthDay.replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호에 포함될 수 없습니다.");
        }
    }

    public static void validateUserId(String userid) {
        if (userid == null || userid.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 빈값이 들어올 수 없습니다.");
        }

        if (!USERID_PATTERN.matcher(userid).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 영문과 숫자만 허용됩니다.");
        }
    }

    public UserModel(String userid, String encodedPassword, String name, String birthDay, String email) {
        validateUserId(userid);
        validateBirthDay(birthDay);
        validateEmail(email);

        if (name == null || name.isBlank() || name.length() < 2) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 2글자 이상으로 작성해 주세요.");
        }

        this.userid = userid;
        this.password = encodedPassword;
        this.name = name;
        this.birthDay = birthDay;
        this.email = email;
    }

    private void validateBirthDay(String birthDay) {
        if (!BIRTHDAY_PATTERN.matcher(birthDay).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식에 맞춰 주세요.");
        }
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public String getUserid() { return userid; }

    public String getPassword() { return password; }

    public String getName() { return name; }

    public String getBirthDay() { return birthDay; }

    public String getEmail() { return email; }

}

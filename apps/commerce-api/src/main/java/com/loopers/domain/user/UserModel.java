package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.regex.Pattern;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_userid", columnNames = {"userid"})
})
public class UserModel extends BaseEntity {

    @Column(nullable = false, length = 16)
    private String userid;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 10)
    private String birthDay;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private UserModel() {}

    private static final Pattern USERID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]+$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=])[a-zA-Z0-9!@#$%^&*()_+\\-=]{8,16}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern BIRTHDAY_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    public static void validateUserId(String userid) {
        Guard.notBlank(userid, "아이디는 빈값이 들어올 수 없습니다.");
        Guard.maxLength(userid, 16, "아이디는 16자를 초과할 수 없습니다.");
        Guard.matches(userid, USERID_PATTERN, "아이디는 영문과 숫자만 허용됩니다.");
    }

    public static void validatePassword(String rawPassword, String birthDay) {
        Guard.notBlank(rawPassword, "비밀번호는 빈값이 들어올 수 없습니다.");
        Guard.matches(rawPassword, PASSWORD_PATTERN, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        if (rawPassword.contains(birthDay.replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비밀번호에 포함될 수 없습니다.");
        }
    }

    public UserModel(String userid, String encodedPassword, String name, String birthDay, String email, UserRole role) {
        validateUserId(userid);
        validateBirthDay(birthDay);
        validateEmail(email);
        Guard.notBlank(name, "이름은 비어있을 수 없습니다.");
        Guard.minLength(name, 2, "이름은 2글자 이상으로 작성해 주세요.");
        Guard.maxLength(name, 50, "이름은 50자를 초과할 수 없습니다.");
        Guard.notNull(role, "역할은 필수입니다.");

        this.userid = userid;
        this.password = encodedPassword;
        this.name = name;
        this.birthDay = birthDay;
        this.email = email;
        this.role = role;
    }

    private void validateBirthDay(String birthDay) {
        Guard.notBlank(birthDay, "생년월일은 빈값이 들어올 수 없습니다.");
        Guard.matches(birthDay, BIRTHDAY_PATTERN, "생년월일은 yyyy-MM-dd 형식에 맞춰 주세요.");
    }

    private void validateEmail(String email) {
        Guard.notBlank(email, "이메일은 빈값이 들어올 수 없습니다.");
        Guard.matches(email, EMAIL_PATTERN, "이메일 형식이 올바르지 않습니다.");
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public String getUserid() { return userid; }

    public String getPassword() { return password; }

    public String getName() { return name; }

    public String getBirthDay() { return birthDay; }

    public String getEmail() { return email; }

    public UserRole getRole() { return role; }
}

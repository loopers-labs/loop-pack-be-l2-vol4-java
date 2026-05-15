package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.PasswordMatcher;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Transient
    private final static Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$");

    private String loginId;
    private String name;
    private String password;
    @Embedded
    private BirthVO birth;
    @Embedded
    private EmailVO email;

    public static UserModel of(String loginId, String name, String password, BirthVO birth, EmailVO email) {
        validatePassword(password, birth);
        return new UserModel(loginId, name, password, birth, email);
    }

    public String getLoginId() {
        return loginId;
    }

    public String getName() {
        return name;
    }

    public BirthVO getBirth() {
        return birth;
    }

    public EmailVO getEmailVO() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void changePassword(String encrypted) {
        this.password = encrypted;
    }

    public void validPasswordChange(String oldPassword, String targetPassword, PasswordMatcher matcher) {
        if (!matcher.matches(oldPassword, this.password)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (matcher.matches(targetPassword, this.password)) {
            throw new IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.");
        }
        validatePassword(targetPassword, this.birth);
    }

    private static void validatePassword(String rawPassword, BirthVO birthVO) {
        if (!PATTERN.matcher(rawPassword).matches()) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
        }
        if (rawPassword.contains(birthVO.toString())) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }
    }
}

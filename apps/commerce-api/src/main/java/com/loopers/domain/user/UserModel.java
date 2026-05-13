package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.support.error.PasswordMatcher;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private String loginId;
    private String name;
    private String password;
    @Embedded
    private BirthVO birth;
    @Embedded
    private EmailVO email;

    public static UserModel of(String loginId, String name, String password, BirthVO birth, EmailVO email) {
        if (password.contains(String.valueOf(birth.toInt()))) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }

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

    public void isPasswordValid(boolean isValid, String message) {
        if (isValid) {
            throw new IllegalArgumentException(message);
        }
    }

    public void validPasswordChange(String oldPassword, String targetPassword, PasswordMatcher matcher) {
        if (!matcher.matches(oldPassword, this.password)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (matcher.matches(targetPassword, this.password)) {
            throw new IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.");
        }
    }
}

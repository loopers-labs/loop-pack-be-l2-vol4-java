package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Column(name = "password", nullable = false)
    private String encodedPassword;

    @Embedded
    private UserName name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Embedded
    private Email email;

    protected UserModel() {}

    public UserModel(LoginId loginId, String encodedPassword, UserName name, LocalDate birthDate, Email email) {
        if (loginId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (name == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (email == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
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

    public String getMaskedName() {
        return name.masked();
    }

    public void changePassword(String newEncodedPassword) {
        if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        this.encodedPassword = newEncodedPassword;
    }
}

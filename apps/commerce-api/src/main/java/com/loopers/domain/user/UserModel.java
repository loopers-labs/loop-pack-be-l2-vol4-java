package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Password password;

    @Embedded
    private Email email;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    private BirthDate birthDate;

    protected UserModel() {}

    public static UserModel create(LoginId loginId, Password password, String name, BirthDate birthDate, Email email) {
        return new UserModel(loginId, password, name, birthDate, email);
    }

    private UserModel(LoginId loginId, Password password, String name, BirthDate birthDate, Email email) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    void changePassword(Password password) {
        this.password = password;
    }

    public LoginId getLoginId() {
        return loginId;
    }

    public Password getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public BirthDate getBirthDate() {
        return birthDate;
    }

    public Email getEmail() {
        return email;
    }
}

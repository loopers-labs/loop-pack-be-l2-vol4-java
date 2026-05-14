package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    private static final String MASK = "*";

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public String getLoginId() {
        return loginId;
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

    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + MASK;
    }
}

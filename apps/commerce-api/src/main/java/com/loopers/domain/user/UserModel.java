package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "users")
@Getter
public class UserModel extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true, length = 10)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false, length = 10)
    private String birthDate;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String gender;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, String birthDate, String email, String gender) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.gender = gender;
    }
}

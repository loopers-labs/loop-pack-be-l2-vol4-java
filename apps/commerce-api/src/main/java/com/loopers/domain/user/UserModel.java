package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    private UserModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static UserModel of(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        return new UserModel(loginId, password, name, birthDate, email);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return UserModel.class.hashCode();
    }
}
package com.loopers.infrastructure.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    private String loginId;
    private String loginPassword;
    private String name;
    private LocalDate birthday;
    private String email;

    protected UserEntity() {}

    public UserEntity(String loginId, String loginPassword, String name, LocalDate birthday, String email) {
        this.loginId = loginId;
        this.loginPassword = loginPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public User toDomain() {
        return new User(getId(), loginId, loginPassword, name, birthday, email,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(User domain) {
        this.loginPassword = domain.getLoginPassword();
    }
}

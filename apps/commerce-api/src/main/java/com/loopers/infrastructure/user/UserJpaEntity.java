package com.loopers.infrastructure.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class UserJpaEntity extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false)
    private String email;

    protected UserJpaEntity() {
    }

    private UserJpaEntity(String loginId, String passwordHash, String name, LocalDate birth, String email) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.birth = birth;
        this.email = email;
    }

    public static UserJpaEntity from(User user) {
        return new UserJpaEntity(
            user.getLoginId(),
            user.getPasswordHash(),
            user.getName(),
            user.getBirth(),
            user.getEmail()
        );
    }

    public User toDomain() {
        return User.reconstruct(getId(), loginId, passwordHash, name, birth, email);
    }

    public void update(User user) {
        this.loginId = user.getLoginId();
        this.passwordHash = user.getPasswordHash();
        this.name = user.getName();
        this.birth = user.getBirth();
        this.email = user.getEmail();
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirth() {
        return birth;
    }

    public String getEmail() {
        return email;
    }
}

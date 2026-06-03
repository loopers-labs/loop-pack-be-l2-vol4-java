package com.loopers.infrastructure.user;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
public class UserJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    protected UserJpaEntity() {}

    UserJpaEntity(Long id, String userId, String name, String email, String password, LocalDate birthDate) {
        if (id != null) {
            setId(id);
        }
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.birthDate = birthDate;
    }
}

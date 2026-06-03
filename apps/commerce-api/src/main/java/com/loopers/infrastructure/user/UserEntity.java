package com.loopers.infrastructure.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * users 테이블 JPA 매핑 전용 엔티티. 순수 도메인(UserModel)과 분리되어 영속 관심사만 담는다.
 * 비밀번호는 해시 상태 그대로 저장한다. 도메인 ↔ 엔티티 변환은 UserEntityMapper가 담당.
 */
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(name = "login_id", unique = true, nullable = false)
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthday;
    private String email;

    protected UserEntity() {}

    public UserEntity(String loginId, String password, String name, LocalDate birthday, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    /**
     * 변경 가능한 상태만 갱신한다(비밀번호 변경). managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     */
    public void applyState(String password) {
        this.password = password;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public String getEmail() {
        return email;
    }
}

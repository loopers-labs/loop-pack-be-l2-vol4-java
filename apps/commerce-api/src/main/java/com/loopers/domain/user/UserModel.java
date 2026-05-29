package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.support.Guard;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_userid", columnNames = {"userid"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Embedded
    private UserId userId;

    @Embedded
    private Password password;

    @Embedded
    private Name name;

    @Embedded
    private BirthDay birthDay;

    @Embedded
    private Email email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    public UserModel(UserId userid, Password password, Name name, BirthDay birthDay, Email email, UserRole role) {
        Guard.notNull(role, "역할은 필수입니다.");
        this.userId = userid;
        this.password = password;
        this.name = name;
        this.birthDay = birthDay;
        this.email = email;
        this.role = role;
    }

    public void changePassword(Password encodedPassword) {
        this.password = encodedPassword;
    }

    public UserId getUserId() { return userId; }

    public Password getPassword() { return password; }

    public Name getName() { return name; }

    public BirthDay getBirthDay() { return birthDay; }

    public Email getEmail() { return email; }

    public UserRole getRole() { return role; }
}

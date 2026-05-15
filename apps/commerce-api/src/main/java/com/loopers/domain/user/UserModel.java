package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id", nullable = false, unique = true, length = 20))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password", nullable = false))
    private EncodedPassword password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private UserName name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "birth_date", nullable = false))
    private BirthDate birthDate;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false))
    private Email email;

    @Builder
    public UserModel(LoginId loginId, EncodedPassword password, UserName name, BirthDate birthDate, Email email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public static UserModel signUp(
        LoginId loginId,
        EncodedPassword password,
        UserName name,
        BirthDate birthDate,
        Email email
    ) {
        return UserModel.builder()
            .loginId(loginId)
            .password(password)
            .name(name)
            .birthDate(birthDate)
            .email(email)
            .build();
    }

    public void changePassword(EncodedPassword newPassword) {
        this.password = newPassword;
    }
}
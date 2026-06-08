package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "login_id", nullable = false, unique = true, length = 20))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "name", nullable = false, length = 50))
    private Name name;

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "birth", nullable = false))
    private Birth birth;

    @Embedded
    @AttributeOverride(name = "value",
        column = @Column(name = "email", nullable = false, unique = true, length = 254))
    private Email email;

    @Column(name = "encoded_password", nullable = false)
    private String encodedPassword;

    public User(LoginId loginId, Name name, Birth birth, Email email, String encodedPassword) {
        this.loginId = loginId;
        this.name = name;
        this.birth = birth;
        this.email = email;
        this.encodedPassword = encodedPassword;
    }

    public static User register(
        LoginId loginId,
        String rawPassword,
        Name name,
        Birth birth,
        Email email,
        PasswordEncoder encoder
    ) {
        Password validated = Password.of(rawPassword, birth);
        String encoded = encoder.encode(validated.value());
        return new User(loginId, name, birth, email, encoded);
    }

    public String maskedName() {
        return this.name.masked();
    }

    public void authenticate(String rawPassword, PasswordEncoder encoder) {
        if (!encoder.matches(rawPassword, this.encodedPassword)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
    }

    public void changePassword(String currentPassword, String newPassword, PasswordEncoder encoder) {
        if (!encoder.matches(currentPassword, this.encodedPassword)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        if (currentPassword.equals(newPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.");
        }
        Password validated = Password.of(newPassword, this.birth);
        this.encodedPassword = encoder.encode(validated.value());
    }
}

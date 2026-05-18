package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String loginId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "encoded_password", nullable = false)
    private String encodedPassword;

    public User(LoginId loginId, Name name, Birth birth, Email email, String encodedPassword) {
        this.loginId = loginId.value();
        this.name = name.value();
        this.birth = birth.value();
        this.email = email.value();
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
        return new Name(this.name).masked();
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
        Password validated = Password.of(newPassword, new Birth(this.birth));
        this.encodedPassword = encoder.encode(validated.value());
    }
}

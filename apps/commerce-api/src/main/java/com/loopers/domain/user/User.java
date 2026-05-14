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

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    private static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9]{1,10}$";
    private static final String NAME_PATTERN = "^[가-힣A-Za-z]+$";

    @Column(nullable = false, unique = true, length = 10)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    private User(String loginId, Password password, String name, BirthDate birthDate, Email email) {
        validateLoginId(loginId);
        validateName(name);

        this.loginId = loginId;
        this.password = password.getValue();
        this.name = name;
        this.birthDate = birthDate.getValue();
        this.email = email.getValue();
    }

    public static User create(String loginId, String password, String name, String birthDate, String email) {
        BirthDate parsedBirthDate = BirthDate.of(birthDate);
        Password parsedPassword = Password.of(password, parsedBirthDate);

        return new User(loginId, parsedPassword, name, parsedBirthDate, Email.of(email));
    }

    public void encryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 비어있을 수 없습니다.");
        }
        this.password = encryptedPassword;
    }

    public void changePassword(String currentPassword, String newPassword) {
        Password validated = Password.of(newPassword, BirthDate.of(this.birthDate.toString()));
        if (validated.getValue().equals(currentPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.");
        }
        this.password = validated.getValue();
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || !loginId.matches(LOGIN_ID_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 형식이 올바르지 않습니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || !name.matches(NAME_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름 형식이 올바르지 않습니다.");
        }
    }
}

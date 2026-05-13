package com.loopers.domain.user;

import java.time.LocalDate;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private EncryptedPassword encryptedPassword;

    @Embedded
    private Name name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    @Builder
    private UserModel(
        String rawLoginId,
        String rawPassword,
        String rawName,
        LocalDate rawBirthDate,
        String rawEmail,
        PasswordEncrypter passwordEncrypter
    ) {
        this.loginId = LoginId.from(rawLoginId);
        this.name = Name.from(rawName);
        this.birthDate = BirthDate.from(rawBirthDate);
        this.email = Email.from(rawEmail);
        this.encryptedPassword = encryptRawPassword(rawPassword, this.birthDate, passwordEncrypter);
    }

    private static EncryptedPassword encryptRawPassword(String rawPassword, BirthDate birthDate, PasswordEncrypter passwordEncrypter) {
        if (birthDate.isContainedIn(rawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        return EncryptedPassword.encrypt(rawPassword, passwordEncrypter);
    }

    public boolean matchesPassword(String rawPassword, PasswordEncrypter passwordEncrypter) {
        return encryptedPassword.matches(rawPassword, passwordEncrypter);
    }

}

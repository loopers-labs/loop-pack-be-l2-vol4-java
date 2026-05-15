package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id"))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "password"))
    private Password password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "birth_date"))
    private BirthDate birthDate;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email"))
    private Email email;

    private String name;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    protected UserModel() {
    }

    public UserModel(
            String loginId, String password, String name,
            String birthDate, String email, Gender gender,
            PasswordEncryptor passwordEncryptor
    ) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        if (gender == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 필수입니다.");
        }
        this.loginId = new LoginId(loginId);
        this.birthDate = new BirthDate(birthDate);
        this.password = new Password(password, this.birthDate, passwordEncryptor);
        this.email = new Email(email);
        this.name = name;
        this.gender = gender;
    }

    public boolean matchesPassword(String password, PasswordEncryptor passwordEncryptor) {
        return this.password.matches(password, passwordEncryptor);
    }

    public void changePassword(String newPassword, PasswordEncryptor passwordEncryptor) {
        if (this.password.matches(newPassword, passwordEncryptor)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "신규 비밀번호는 기존 비밀번호와 달라야 합니다.");
        }

        this.password = new Password(newPassword, this.birthDate, passwordEncryptor);
    }
}

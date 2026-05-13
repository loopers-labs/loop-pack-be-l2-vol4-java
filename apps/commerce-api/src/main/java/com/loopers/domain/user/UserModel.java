package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class UserModel extends BaseEntity {

    private LoginId loginId;
    private EncryptedPassword encryptedPassword;
    private Name name;
    private BirthDate birthDate;
    private Email email;

    protected UserModel() {}

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

}

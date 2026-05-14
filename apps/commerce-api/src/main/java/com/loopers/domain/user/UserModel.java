package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id")
    }
)
public class UserModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private Name name;

    @Embedded
    private BirthDate birthDate;

    @Embedded
    private Email email;

    @Embedded
    private EncodedPassword encodedPassword;

    public UserModel(LoginId loginId, Name name, BirthDate birthDate, Email email, EncodedPassword encodedPassword) {
        if (loginId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (name == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
        if (email == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }
        if (encodedPassword == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
        this.loginId = loginId;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.encodedPassword = encodedPassword;
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return this.encodedPassword.matches(rawPassword, encoder);
    }

    public boolean doesNotMatchPassword(String rawPassword, PasswordEncoder encoder) {
        return !matchesPassword(rawPassword, encoder);
    }

    public void changePassword(PasswordEncoder encoder, String newRawPassword) {
        this.encodedPassword = EncodedPassword.create(encoder, newRawPassword);
    }
}

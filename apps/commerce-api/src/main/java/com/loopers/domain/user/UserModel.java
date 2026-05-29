package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserModel extends BaseEntity {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",.<>/?|\\\\`~]{8,16}$");

    @Getter(AccessLevel.NONE)
    @Embedded
    private LoginId loginId;

    private String password;
    private String name;

    @Getter(AccessLevel.NONE)
    @Embedded
    private Birth birth;

    @Getter(AccessLevel.NONE)
    @Embedded
    private Email email;

    public UserModel(String loginId, String password, String name, String birth, String email) {
        validateName(name);
        validatePass(password, birth);

        this.loginId = new LoginId(loginId);
        this.password = password;
        this.name = name;
        this.birth = new Birth(birth);
        this.email = new Email(email);
    }

    public String getLoginId() {
        return loginId.getValue();
    }

    public String getBirth() {
        return birth.getValue();
    }

    public String getEmail() {
        return email.getValue();
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
    }

    private void validatePass(String password, String birth) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (password.contains(birth)) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
    }

    public void encodePassword(PasswordEncoder encoder) {
        this.password = encoder.encode(this.password);
    }

    /** 이름의 마지막 글자를 '*' 로 마스킹한다. name 은 생성자에서 검증되어 항상 1자 이상. */
    public String getMaskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    /** 비밀번호를 변경한다. 형식 위반·생년월일 포함·현재 비밀번호와 동일 시 BAD_REQUEST. */
    public void changePassword(String newPassword, PasswordEncoder encoder) {
        validatePass(newPassword, this.getBirth());
        if (encoder.matches(newPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.password = encoder.encode(newPassword);
    }
}

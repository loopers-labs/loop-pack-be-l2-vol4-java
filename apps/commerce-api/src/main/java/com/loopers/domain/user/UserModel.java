package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Entity
@Table(name ="users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern BIRTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",.<>/?|\\\\`~]{8,16}$");

    private String loginId;
    private String password;
    private String name;
    private String birth;
    private String email;

    public UserModel(String loginId, String password, String name, String birth, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateEmail(email);
        validateBirth(birth);
        validatePass(password, birth);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birth = birth;
        this.email = email;
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
    }

    private void validateBirth(String birth) {
        if (birth == null || !BIRTH_PATTERN.matcher(birth).matches()) {
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
        validatePass(newPassword, this.birth);
        if (encoder.matches(newPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.password = encoder.encode(newPassword);
    }
}

package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private String loginId;
    private String loginPassword;
    private String name;
    private LocalDate birthday;
    private String email;

    public User(String loginId, String loginPassword, String name, LocalDate birthday, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateEmail(email);
        validateBirthday(birthday);

        this.loginId = loginId;
        this.loginPassword = loginPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public void changePassword(String newPassword) {
        this.loginPassword = newPassword;
    }

    private void validateLoginId(String loginId) {
        if (StringUtils.isBlank(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 아이디는 비어있을 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (!name.matches("^(?=.{2,50}$)[가-힣a-zA-Z]+( [가-힣a-zA-Z]+)*$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 이름을 입력하세요.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 이메일을 입력하세요.");
        }
    }

    private void validateBirthday(LocalDate birthday) {
        if (birthday == null || !birthday.isBefore(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 생일을 입력하세요.");
        }
    }
}

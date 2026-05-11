package com.loopers.domain.member;

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
import java.time.format.DateTimeFormatter;

@Entity
@Table(name="member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    private String loginId;
    private String loginPassword;
    private String name ;
    private LocalDate birthday;
    private String email;

    public Member(String loginId, String loginPassword, String name, LocalDate birthday, String email) {
        validateLoginId(loginId);
        validateLoginPassword(loginPassword, birthday);
        validateName(name);
        validateEmail(email);
        validBirthday(birthday);

        this.loginId = loginId;
        this.loginPassword = loginPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    private void validateLoginId(String loginId) {
        if (StringUtils.isBlank(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 아이디는 비어있을 수 없습니다.");
        }
    }

    private void validateLoginPassword(String loginPassword, LocalDate birthday) {
        if (loginPassword == null || !loginPassword.matches("^[a-zA-Z0-9\\p{Punct}]{8,16}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 패스워드는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }

        String yyyyMMdd = birthday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String MMdd = birthday.format(DateTimeFormatter.ofPattern("MMdd"));

        if (StringUtils.containsAny(loginPassword, MMdd, yyyyMMdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 패스워드에 생년월일을 포함할 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }

        if(!name.matches("^(?=.{2,50}$)[가-힣a-zA-Z]+( [가-힣a-zA-Z]+)*$")){
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 이름을 입력하세요.");
        }
    }

    private void validateEmail(String email) {
        if(email == null || !email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")){
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 이메일을 입력하세요.");
        }
    }

    private void validBirthday(LocalDate birthday) {
        if(birthday == null || !birthday.isBefore(LocalDate.now())){
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 생일을 입력하세요.");
        }
    }

}

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

@Entity
@Table(name="member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberModel extends BaseEntity {
    private String loginId;
    private String loginPassword;
    private String name ;
    private LocalDate birthday;
    private String email;

    public MemberModel(String loginId, String loginPassword, String name, LocalDate birthday, String email) {
        validateLoginId();
        validateLoginPassword();
        validateEmail();

        this.loginId = loginId;
        this.loginPassword = loginPassword;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    private void validateLoginId() {
        if (StringUtils.isBlank(this.loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 아이디는 비어있을 수 없습니다.");
        }
    }

    private void validateLoginPassword() {
        if (!this.loginPassword.matches("^\\p{ASCII}&&\\S{8,16}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 패스워드는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
    }

    private void validateEmail(){
        if(!this.email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")){
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 형식의 이메일을 입력하세요.");
        }
    }
}

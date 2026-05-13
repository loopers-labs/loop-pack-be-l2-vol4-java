package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user")
public class UserModel {
    private static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9]+$";
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final int PW_LETTER_MAX_SIZE = 20;
    private static final int PW_LETTER_MIN_SIZE = 8;
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/~`|\\\\]+$";
    private static final String BIRTH_DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
    public UserModel(String loginId, String password, String name, String birthDate, String email) {
        if(!loginId.matches(LOGIN_ID_PATTERN)){
            throw new CoreException((ErrorType.BAD_REQUEST), "로그인 id는 영문과 숫자만 허용됩니다.");
        }
        if((password.length() >PW_LETTER_MAX_SIZE)||password.length()<PW_LETTER_MIN_SIZE){
            throw new CoreException((ErrorType.BAD_REQUEST), "비밀번호는 8~20자를 이내로 작성해주시기 바랍니다.");
        }
        if(!password.matches(PASSWORD_PATTERN)){
            throw new CoreException((ErrorType.BAD_REQUEST),"비밀번호는 영문 대소문자 숫자 특수문자로만 구성됩니다.");
        }
        if (name==null||name.isBlank()){
            throw new CoreException((ErrorType.BAD_REQUEST),"이름을 적어주시기 바랍니다." );
        }
        if (!email.matches(EMAIL_PATTERN)){
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일을 형태에 알맞게 작성해주시기 바랍니다.");
        }
        if(!birthDate.matches(BIRTH_DATE_PATTERN)){
            throw new CoreException((ErrorType.BAD_REQUEST),"생년월일 타입이 yyyy-mm-dd 형태가 아닙니다.");
        }
    }
}

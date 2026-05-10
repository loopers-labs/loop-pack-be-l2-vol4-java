package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.value.BirthVO;
import com.loopers.domain.value.EmailVO;
import com.loopers.domain.value.PasswordVO;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private String loginId;
    private String name;
    private BirthVO birth;
    private PasswordVO passwordVO;
    private EmailVO emailVO;

    protected UserModel() {
    }

    private UserModel(String loginId, String name, BirthVO birth, PasswordVO passwordVO, EmailVO emailVO) {
        if (passwordVO.value().contains(String.valueOf(birth.toInt()))) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 생년월일은 비밀번호 내에 포함할 수 없습니다.");
        }

        this.loginId = loginId;
        this.name = name;
        this.birth = birth;
        this.passwordVO = passwordVO;
        this.emailVO = emailVO;
    }

    public static UserModel of(String loginId, String name, BirthVO birth, PasswordVO passwordVO, EmailVO email) {
        return new UserModel(loginId, name, birth, passwordVO, email);
    }

    public String getLoginId() {
        return loginId;
    }
}

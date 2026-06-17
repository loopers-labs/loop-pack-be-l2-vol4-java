package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String loginPw;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    protected UserModel() {}

    public UserModel(String loginId, String loginPw, String email, String nickname) {
        validate(loginId, loginPw, email, nickname);
        this.loginId = loginId;
        this.loginPw = loginPw;
        this.email = email;
        this.nickname = nickname;
    }

    private void validate(String loginId, String loginPw, String email, String nickname) {
        if (loginId == null || loginId.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.");
        if (loginPw == null || loginPw.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 유효하지 않습니다.");
        if (nickname == null || nickname.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "닉네임은 비어있을 수 없습니다.");
    }

    public void changePassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        this.loginPw = newPassword;
    }

    public String getLoginId() { return loginId; }
    public String getLoginPw() { return loginPw; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
}

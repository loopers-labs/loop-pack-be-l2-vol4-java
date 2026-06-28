package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

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

    @Column(name = "birth_date")
    private LocalDate birthDate;

    protected UserModel() {}

    public UserModel(String loginId, String loginPw, String email, String nickname, LocalDate birthDate) {
        validate(loginId, loginPw, email, nickname);
        this.loginId = loginId;
        this.loginPw = loginPw;
        this.email = email;
        this.nickname = nickname;
        this.birthDate = birthDate;
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

    public boolean authenticate(String loginId, String loginPw) {
        return this.loginId.equals(loginId) && this.loginPw.equals(loginPw);
    }

    public void changePassword(String oldPw, String newPw) {
        if (!this.loginPw.equals(oldPw))
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        if (newPw == null || newPw.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 비어있을 수 없습니다.");
        this.loginPw = newPw;
    }

    public String getLoginId() { return loginId; }
    public String getLoginPw() { return loginPw; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public LocalDate getBirthDate() { return birthDate; }
}

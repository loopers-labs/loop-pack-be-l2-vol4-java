package com.loopers.member.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,20}$");

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MemberStatus status;

    protected MemberModel() {}

    public MemberModel(String loginId, String password) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자 4~20자여야 합니다.");
        }
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.status = MemberStatus.ACTIVE;
    }

    public boolean verifyPassword(String rawPassword) {
        return this.password.equals(rawPassword);
    }

    public void changePassword(String currentPassword, String newPassword) {
        if (!verifyPassword(currentPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 비어있을 수 없습니다.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        this.password = newPassword;
    }
}

package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "encoded_password", nullable = false)
    private String encodedPassword;

    protected User() {}

    public User(String loginId, String encodedPassword) {
        validateLoginId(loginId);
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        this.loginId = loginId;
        this.encodedPassword = encodedPassword;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getEncodedPassword() {
        return encodedPassword;
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.encodedPassword);
    }

    /**
     * 비밀번호를 변경한다.
     * - 현재 비밀번호 불일치 시 예외
     * - 새 비밀번호가 현재와 동일하면 예외
     */
    public void changePassword(String currentRawPw, String newRawPw, PasswordEncoder encoder) {
        if (!encoder.matches(currentRawPw, this.encodedPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (encoder.matches(newRawPw, this.encodedPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        this.encodedPassword = encoder.encode(newRawPw);
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
    }
}

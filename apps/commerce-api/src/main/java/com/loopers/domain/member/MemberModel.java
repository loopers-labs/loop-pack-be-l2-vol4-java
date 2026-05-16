package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";
    private static final String BIRTHDATE_PATTERN = "^\\d{8}$";
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9\\p{Punct}]{8,16}$";

    private String userId;
    private String password;
    private String email;
    private String username;
    private String birthDate;

    protected MemberModel() {}

    public MemberModel(String userId, String password, String email, String username, String birthDate) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!userId.matches("^[A-Za-z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
        if (username == null || username.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (email == null || !email.matches(EMAIL_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
        if (birthDate == null || !birthDate.matches(BIRTHDATE_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 YYYYMMDD 형식이어야 합니다.");
        }
        validatePassword(password, birthDate);

        this.userId = userId;
        this.password = hashPassword(password);
        this.email = email;
        this.username = username;
        this.birthDate = birthDate;
    }

    private void validatePassword(String password, String birthDate) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (password.contains(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일이 포함될 수 없습니다.");
        }
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "비밀번호 암호화에 실패했습니다.");
        }
    }

    public String getUserId() { return userId; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getBirthDate() { return birthDate; }

    public String getMaskedUsername() {
        return username.substring(0, username.length() - 1) + "*";
    }

    public void updatePassword(String currentPassword, String newPassword) {
        if (!hashPassword(currentPassword).equals(this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        validatePassword(newPassword, this.birthDate);
        if (hashPassword(newPassword).equals(this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        this.password = hashPassword(newPassword);
    }
}
package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.util.regex.Pattern;

public class User {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final LocalDate MIN_BIRTH = LocalDate.of(1900, 1, 1);

    private Long id;
    private String loginId;
    private String passwordHash;
    private String name;
    private LocalDate birth;
    private String email;

    public User(String loginId, String passwordHash, String name, LocalDate birth, String email) {
        this(null, loginId, passwordHash, name, birth, email);
    }

    private User(Long id, String loginId, String passwordHash, String name, LocalDate birth, String email) {
        validateLoginId(loginId);
        validatePasswordHash(passwordHash);
        validateName(name);
        validateBirth(birth);
        validateEmail(email);

        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.birth = birth;
        this.email = email;
    }

    public static User reconstruct(Long id, String loginId, String passwordHash, String name, LocalDate birth, String email) {
        return new User(id, loginId, passwordHash, name, birth, email);
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirth() {
        return birth;
    }

    public String getEmail() {
        return email;
    }

    public String maskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    public void changePasswordHash(String newPasswordHash) {
        validatePasswordHash(newPasswordHash);
        this.passwordHash = newPasswordHash;
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자로만 이루어진 4~20자여야 합니다.");
        }
    }

    private void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호 해시는 비어있을 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 1~50자여야 합니다.");
        }
    }

    private void validateBirth(LocalDate birth) {
        if (birth == null || birth.isBefore(MIN_BIRTH) || birth.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일이 올바르지 않습니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }
}

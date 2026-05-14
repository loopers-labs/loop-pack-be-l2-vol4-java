package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 50;
    /** 이름: 한글(완성형 가-힣) / 영문 대소문자 / 공백만 허용 (자모 분리, 숫자, 특수문자 금지) */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣A-Za-z ]+$");

    @Column(name = "login_id", unique = true, nullable = false)
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthday;
    private String email;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, LocalDate birthday, String email) {
        // 검증 순서: birthday 먼저 (password 검증이 birthday를 사용하기 때문)
        this.birthday = validateBirthday(birthday);
        this.loginId = new LoginId(loginId).getValue();
        this.password = encrypt(new Password(password, this.birthday).getValue());
        this.name = validateName(name);
        this.email = new Email(email).getValue();
    }

    // --- 검증 메서드 ---

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 null이거나 공백일 수 없습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "이름은 " + NAME_MAX_LENGTH + "자 이하여야 합니다."
            );
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름 형식이 올바르지 않습니다.");
        }
        return name;
    }

    private static LocalDate validateBirthday(LocalDate birthday) {
        if (birthday == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 null일 수 없습니다.");
        }
        if (birthday.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }
        return birthday;
    }

    private static String encrypt(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    // --- 비밀번호 관련 도메인 메서드 ---

    /**
     * 입력된 raw 비밀번호가 저장된 hash와 일치하는지 확인
     */
    public boolean matchesPassword(String rawPassword) {
        return this.password.equals(encrypt(rawPassword));
    }

    /**
     * 비밀번호 변경. raw 비밀번호 검증 후 해싱하여 저장
     */
    public void changePassword(String newRawPassword) {
        this.password = encrypt(new Password(newRawPassword, this.birthday).getValue());
    }

    // --- Getter ---

    public LoginId getLoginId() { return new LoginId(loginId); }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public LocalDate getBirthday() { return birthday; }
    public String getEmail() { return email; }
}

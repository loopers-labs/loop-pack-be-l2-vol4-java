package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()]{8,16}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    protected UserModel() {}

    public static UserModel create(
            String userId,
            String rawPassword,
            String name,
            LocalDate birthDate,
            String email,
            PasswordEncryptor passwordEncryptor
    ) {
        Objects.requireNonNull(passwordEncryptor, "passwordEncryptor must not be null");
        validateUserId(userId);
        validateBirthDate(birthDate);
        validatePassword(rawPassword, birthDate);
        validateName(name);
        validateEmail(email);

        return new UserModel(userId, passwordEncryptor.encode(rawPassword), name, birthDate, email);
    }

    private UserModel(String userId, String encodedPassword, String name, LocalDate birthDate, String email) {
        this.userId = userId;
        this.password = encodedPassword;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public void changePassword(String currentPassword, String newPassword, PasswordEncryptor passwordEncryptor) {
        Objects.requireNonNull(passwordEncryptor, "passwordEncryptor must not be null");
        verifyPassword(currentPassword, passwordEncryptor);
        if (passwordEncryptor.matches(newPassword, this.password)) {
            throw new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT);
        }
        validatePassword(newPassword, this.birthDate);
        this.password = passwordEncryptor.encode(newPassword);
    }

    public void verifyPassword(String rawPassword, PasswordEncryptor passwordEncryptor) {
        Objects.requireNonNull(passwordEncryptor, "passwordEncryptor must not be null");
        if (!passwordEncryptor.matches(rawPassword, this.password)) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.USER_ID_REQUIRED);
        }
        if (!LOGIN_ID_PATTERN.matcher(userId).matches()) {
            throw new CoreException(ErrorType.USER_ID_INVALID_FORMAT);
        }
    }

    private static void validatePassword(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.PASSWORD_REQUIRED);
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.PASSWORD_INVALID_FORMAT);
        }
        String birth = birthDate.toString().replace("-", "");
        if (password.contains(birth)) {
            throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.USER_NAME_REQUIRED);
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.EMAIL_REQUIRED);
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.EMAIL_INVALID_FORMAT);
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.BIRTH_DATE_REQUIRED);
        }
    }

    public String getMaskedName() {
        if (name.length() <= 1) {
            return "*";
        }
        return name.substring(0, name.length() - 1) + "*";
    }

    public String getUserId() {
        return userId;
    }

    String getEncodedPassword() {
        return password;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getEmail() {
        return email;
    }

}

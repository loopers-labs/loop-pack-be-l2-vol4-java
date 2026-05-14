package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE member SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{5,20}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^([가-힣]{2,20}|[a-zA-Z]{2,20})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");

    @Builder
    public MemberModel(String loginId, String password, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validateName(name);
        validateEmail(email);
        validateBirthDate(birthDate);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public void updatePassword(String encryptedPassword, String rawPassword) {
        validatePassword(rawPassword, this.birthDate);
        this.password = encryptedPassword;
    }

    public String getMaskedName() {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, name.length() - 1) + "*";
    }

    public static void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.INVALID_LOGIN_ID);
        }
    }

    public static void validatePassword(String password, LocalDate birthDate) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.INVALID_PASSWORD);
        }

        String birthDateStr = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (password.contains(birthDateStr)) {
            throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDATE);
        }
    }

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.INVALID_NAME);
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.INVALID_EMAIL);
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new CoreException(ErrorType.REQUIRED_BIRTHDATE);
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.INVALID_BIRTHDATE);
        }
        if (birthDate.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new CoreException(ErrorType.INVALID_BIRTHDATE);
        }
    }
}

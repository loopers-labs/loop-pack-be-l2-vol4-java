package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Entity
@Table(name = "users")
public class UserModel extends BaseEntity {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final String LOGIN_ID_REGEX = "^[a-zA-Z0-9]{1,10}$";
    private static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String PASSWORD_REGEX = "^[\\x21-\\x7E]{8,16}$";
    private static final String NAME_REGEX = "^[가-힣]{2,10}$";

    private String loginId;
    private String password;
    private String name;
    private String email;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    protected UserModel() {}

    public UserModel(String loginId, String password, String name, String email, String birthDate, Gender gender) {
        if (loginId == null || !loginId.matches(LOGIN_ID_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "loginId는 영문 및 숫자 10자 이내여야 합니다.");
        }
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        try {
            this.birthDate = LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
        if (!this.birthDate.isBefore(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 오늘 이전이어야 합니다.");
        }
        if (name == null || !name.matches(NAME_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글 2~10자여야 합니다.");
        }
        // [fix] gender null 검증 누락으로 성별 없는 요청이 200을 반환하던 버그 수정
        if (gender == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 비어있을 수 없습니다.");
        }

        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.gender = gender;
        validatePassword(password);
        this.password = password;
    }

    public void encodePassword(PasswordEncryptor encryptor) {
        this.password = encryptor.encrypt(this.password);
    }

    // "현재 비밀번호와 동일 불가" 검증은 이 메서드에서 하지 않는다.
    // 저장된 비밀번호는 암호화된 상태라 평문과 직접 비교가 불가능하고,
    // PasswordEncoder는 외부 의존성이므로 도메인 모델에 주입하지 않는다.
    // 해당 검증은 PasswordEncoder를 보유한 Service 레이어에서 담당한다.
    public void changePassword(String newPassword) {
        validatePassword(newPassword);
        this.password = newPassword;
    }

    // 비밀번호 유효성 규칙이 회원가입과 비밀번호 변경에서 동일하기 때문에 공통 메서드로 추출한다.
    // 규칙이 변경될 경우 이 메서드 하나만 수정하면 두 흐름 모두 반영되어 유지보수 누락을 방지한다.
    private void validatePassword(String password) {
        String birthDateNumeric = this.birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (password == null || !password.matches(PASSWORD_REGEX)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (password.contains(birthDateNumeric)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public String getLoginId() { return loginId; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getMaskedName() { return name.substring(0, name.length() - 1) + "*"; }
    public String getEmail() { return email; }
    public LocalDate getBirthDate() { return birthDate; }
    public Gender getGender() { return gender; }
}

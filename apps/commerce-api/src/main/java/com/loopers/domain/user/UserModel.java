package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * User Aggregate 루트 — 순수 도메인 객체. 검증/비밀번호 같은 비즈니스 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.user.UserEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 UserEntityMapper가 처리한다.
 *
 * 게이트 VO(LoginId/Email/Password)는 생성·검증 경계에서만 사용하고, 내부에는 검증을 통과한
 * primitive 값으로 보관한다(영속 컬럼과 1:1). 비밀번호는 항상 해시 상태로만 보관한다.
 */
public class UserModel {

    private static final int NAME_MAX_LENGTH = 50;
    /** 이름: 한글(완성형 가-힣) / 영문 대소문자 / 공백만 허용 (자모 분리, 숫자, 특수문자 금지) */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣A-Za-z ]+$");

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private final String loginId;
    private String password;
    private final String name;
    private final LocalDate birthday;
    private final String email;

    public UserModel(String loginId, String password, String name, LocalDate birthday, String email, PasswordEncoder passwordEncoder) {
        // 검증 순서: birthday 먼저 (password 검증이 birthday를 사용하기 때문)
        this.id = null;
        this.birthday = validateBirthday(birthday);
        this.loginId = new LoginId(loginId).getValue();
        this.password = passwordEncoder.encode(new Password(password, this.birthday).getValue());
        this.name = validateName(name);
        this.email = new Email(email).getValue();
    }

    private UserModel(Long id, String loginId, String password, String name, LocalDate birthday, String email) {
        this.id = id;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    /**
     * 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용).
     * 저장된 값은 이미 검증을 통과했고 password는 해시 상태이므로 재검증·재해싱하지 않는다.
     */
    public static UserModel reconstitute(Long id, String loginId, String password, String name, LocalDate birthday, String email) {
        return new UserModel(id, loginId, password, name, birthday, email);
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

    // --- 비밀번호 관련 도메인 메서드 ---

    /**
     * 입력된 raw 비밀번호가 저장된 hash와 일치하는지 확인
     */
    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(rawPassword, this.password);
    }

    /**
     * 비밀번호 변경. raw 비밀번호 검증 후 해싱하여 저장
     */
    public void changePassword(String newRawPassword, PasswordEncoder passwordEncoder) {
        this.password = passwordEncoder.encode(new Password(newRawPassword, this.birthday).getValue());
    }

    // --- Getter ---

    public Long getId() { return id; }
    public LoginId getLoginId() { return new LoginId(loginId); }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public LocalDate getBirthday() { return birthday; }
    public String getEmail() { return email; }
}

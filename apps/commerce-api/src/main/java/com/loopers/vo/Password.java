package com.loopers.vo;

import com.loopers.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.List;

public final class Password {
    private final String value;

    public Password(String raw, Birthdate birthdate) {
        List<String> errors = validate(raw, birthdate != null ? birthdate.getValue() : null);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
        this.value = raw;
    }

    public Password(String raw) { this(raw, null); }

    public String getValue() { return value; }

    public static List<String> validate(String raw, String birthdateYYYYMMDD) {
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.isBlank()) { errors.add("비밀번호를 입력해주세요."); return errors; }
        if (raw.length() < 8 || raw.length() > 16) errors.add("비밀번호는 8~16자여야 합니다.");
        if (!raw.matches("[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?]+")) errors.add("비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        if (birthdateYYYYMMDD != null && containsBirthdate(raw, birthdateYYYYMMDD)) errors.add("비밀번호에 생년월일이 포함될 수 없습니다.");
        return errors;
    }

    private static boolean containsBirthdate(String pw, String bd) {
        return pw.contains(bd) || pw.contains(bd.substring(2)) || pw.contains(bd.substring(4)) || pw.contains(bd.substring(0, 4));
    }
}

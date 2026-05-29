package com.loopers.domain.user.vo;

import com.loopers.support.Guard;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class RawPassword {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=])[a-zA-Z0-9!@#$%^&*()_+\\-=]{8,16}$");

    private final String value;

    public RawPassword(String value) {
        Guard.notBlank(value, "비밀번호는 빈값이 들어올 수 없습니다.");
        Guard.matches(value, PASSWORD_PATTERN, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        this.value = value;
    }
}

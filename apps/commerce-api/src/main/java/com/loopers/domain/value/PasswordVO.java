package com.loopers.domain.value;

import java.util.regex.Pattern;

public record PasswordVO(
        String value
){
    private static final Pattern PATTERN =
            Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$");

    public PasswordVO {
        if (value.length() > 16 || value.length() < 8) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
        }
    }
}

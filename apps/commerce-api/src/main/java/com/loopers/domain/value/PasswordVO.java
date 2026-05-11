package com.loopers.domain.value;

import java.util.regex.Pattern;

public record PasswordVO(
    String password
){
    private static final Pattern PATTERN =
            Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$");

    public PasswordVO {
        if (password.length() > 16 || password.length() < 8) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
        }
        if (!PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다");
        }
    }
}

package com.loopers.interfaces.api;

public record AuthHeaders(String loginId, String loginPw) {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
}

package com.loopers.interfaces.auth;

public final class AuthHeaders {

    public static final String LOGIN_ID = "X-Loopers-LoginId";
    public static final String LOGIN_PW = "X-Loopers-LoginPw";

    public static final String ADMIN_LDAP = "X-Loopers-Ldap";
    public static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private AuthHeaders() {
    }
}

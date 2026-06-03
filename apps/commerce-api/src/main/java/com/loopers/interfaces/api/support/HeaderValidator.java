package com.loopers.interfaces.api.support;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public final class HeaderValidator {

    public static final String LOGIN_ID = "X-Loopers-LoginId";
    public static final String LOGIN_PW = "X-Loopers-LoginPw";
    public static final String ADMIN_LDAP = "X-Loopers-Ldap";

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private HeaderValidator() {}

    public static void validateUser(String loginId, String loginPw) {
        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 헤더가 올바르지 않습니다.");
        }
    }

    public static void validateAdmin(String ldap) {
        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "어드민 LDAP 헤더가 올바르지 않습니다.");
        }
    }
}

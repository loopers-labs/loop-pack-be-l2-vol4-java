package com.loopers.common.interfaces.api;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public final class AdminAuth {

    public static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private AdminAuth() {}

    public static void verify(String ldap) {
        if (!ADMIN_LDAP.equals(ldap)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "관리자 인증이 필요합니다.");
        }
    }
}

package com.loopers.interfaces.api.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public final class AdminAuth {

    private AdminAuth() {}

    public static void validate(String ldap) {
        if (!AuthHeaders.ADMIN_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
    }
}

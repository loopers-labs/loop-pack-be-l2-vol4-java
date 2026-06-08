package com.loopers.interfaces.api.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 어드민 인증이 필요한 파라미터에 붙인다. X-Loopers-Ldap 헤더 존재를 강제하고 ldap id(String)를 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Admin {
}

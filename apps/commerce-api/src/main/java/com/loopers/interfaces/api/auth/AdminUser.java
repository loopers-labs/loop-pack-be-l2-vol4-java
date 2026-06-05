package com.loopers.interfaces.api.auth;

/** 어드민 식별 정보. X-Loopers-Ldap 헤더의 ldap 값. */
public record AdminUser(String ldap) {
}

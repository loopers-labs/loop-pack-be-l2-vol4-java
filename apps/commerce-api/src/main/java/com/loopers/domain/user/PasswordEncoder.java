package com.loopers.domain.user;

/**
 * 비밀번호 해싱·검증을 위한 도메인 포트.
 * 구체 알고리즘(BCrypt 등)은 infrastructure 어댑터가 제공한다.
 */
public interface PasswordEncoder {

    /** raw 비밀번호를 해싱하여 저장 가능한 문자열로 반환한다. */
    String encode(String rawPassword);

    /** raw 비밀번호가 저장된 해시와 일치하는지 검증한다. */
    boolean matches(String rawPassword, String encodedPassword);
}

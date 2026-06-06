package com.loopers.interfaces.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 사용자 식별용 파라미터 마커.
 * {@code X-Loopers-LoginId} 헤더로 사용자를 식별해 해당 사용자의 식별자(userId)를 주입한다.
 * 인증/인가(비밀번호 검증 등)는 과제 스코프가 아니므로 수행하지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}

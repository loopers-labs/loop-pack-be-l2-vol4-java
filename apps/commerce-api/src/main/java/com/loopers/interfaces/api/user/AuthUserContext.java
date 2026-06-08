package com.loopers.interfaces.api.user;

/**
 * 인증된 사용자 정보. AuthUserArgumentResolver 가 인증 헤더를 검증한 뒤 주입한다.
 *
 * - loginId: 인증 헤더로 식별된 사용자의 외부 식별자
 * - userId: DB PK (도메인 식별자). 인증 과정에서 이미 조회되므로 함께 담아 반환한다.
 *   Facade에서 별도로 UserService.getUser()를 재호출할 필요가 없다.
 */
public record AuthUserContext(String loginId, Long userId) {}

package com.loopers.interfaces.api.queue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 대기열 엔드포인트 전용 경량 사용자 식별 애노테이션.
 *
 * <p>{@code @AuthUser}(비밀번호 BCrypt 대조)와 달리, 대기열 입장/순번조회는 고빈도 폴링 경로라
 * 매 호출마다 BCrypt를 도는 비용이 과도하다. 이 애노테이션이 붙은 파라미터는
 * {@link QueueUserArgumentResolver}가 로그인 ID 헤더만으로 userId를 해석한다(비밀번호 미검증).
 *
 * <p>보안은 실제 주문/결제({@code POST /orders}, {@code /payments})가 그대로 {@code @AuthUser}로
 * 지키므로, 대기열에서의 위장은 "남의 순번을 대신 조회/입장"하는 정도의 낮은 위험에 그친다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueueUser {}

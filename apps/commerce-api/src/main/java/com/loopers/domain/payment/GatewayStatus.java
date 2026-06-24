package com.loopers.domain.payment;

/** PG 조회 결과의 상태와 실패 사유. 성공 시 reason은 null. */
public record GatewayStatus(String status, String reason) {
}

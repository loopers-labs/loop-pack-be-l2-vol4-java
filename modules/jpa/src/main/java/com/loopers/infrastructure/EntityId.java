package com.loopers.infrastructure;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * 엔티티 식별자(PK)를 {@code {code}_{ULID}} 형식으로 생성한다.
 *
 * <ul>
 *   <li>{@code code} : 도메인을 3글자로 축약한 코드 (예: USR, BRD, PRD)</li>
 *   <li>{@code ULID} : 26자 Crockford Base32. monotonic 생성이라 시간 정렬 + 스레드 안전</li>
 * </ul>
 *
 * 전역 유일성은 ULID(80bit 랜덤 + ms 타임스탬프)가 사실상 보장하며,
 * 최종 보장은 DB의 PK/복합 UNIQUE 제약이 담당한다.
 */
public final class EntityId {

    private EntityId() {
    }

    public static String generate(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("도메인 코드는 필수입니다.");
        }
        return code + "_" + UlidCreator.getMonotonicUlid();
    }
}

package com.loopers.support.tsid;

import io.hypersistence.tsid.TSID;

/**
 * 애플리케이션 생성 식별자(TSID). 64bit Long, 시간 정렬(단조 증가)이라 PK로 쓰면
 * "OrderByIdDesc = 최신순" 정렬 의미가 보존되고, auto-increment와 달리 비순차라 열거가 불가능하다.
 * <p>
 * 외부 라이브러리(hypersistence-tsid) 의존을 이 한 곳에 가둬, 도메인은 이 유틸만 바라보게 한다.
 */
public final class TsidGenerator {

    private TsidGenerator() {}

    /** 새 식별자를 즉시 발급한다. 스레드 안전(기본 팩토리). */
    public static long generate() {
        return TSID.Factory.getTsid().toLong();
    }
}

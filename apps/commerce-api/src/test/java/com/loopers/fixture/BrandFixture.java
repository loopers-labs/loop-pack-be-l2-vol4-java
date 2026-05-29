package com.loopers.fixture;

import com.loopers.domain.brand.BrandModel;

/**
 * 브랜드 테스트 픽스처 — Object Mother 패턴
 *
 * 사용 원칙:
 *   - createModel()              : 완전히 유효한 기본 객체가 필요할 때
 *   - 상수 직접 참조 (NAME, DESCRIPTION) : "한 필드만 깨뜨리기" 패턴에서 나머지 VALID 값으로 사용
 *   - createModel(name) 오버로드    : 중복 이름 시나리오 등에서 이름만 변경 시
 */
public class BrandFixture {

    public static final String NAME        = "나이키";
    public static final String DESCRIPTION = "세계적인 스포츠 브랜드";

    public static BrandModel createModel() {
        return new BrandModel(NAME, DESCRIPTION);
    }

    public static BrandModel createModel(String name) {
        return new BrandModel(name, DESCRIPTION);
    }
}

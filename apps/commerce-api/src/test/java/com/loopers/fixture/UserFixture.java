package com.loopers.fixture;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.user.UserV1Dto;

/**
 * 회원 테스트 픽스처 — Object Mother 패턴
 *
 * 사용 원칙:
 *   - createModel() / createRequest() : 완전히 유효한 기본 객체가 필요할 때
 *   - 상수 직접 참조 (PASSWORD, NAME ...) : "한 필드만 깨뜨리기" 패턴에서 나머지 VALID 값으로 사용
 *   - 한 필드만 다른 객체 : 테스트 안에서 new UserModel("otherId", UserFixture.PASSWORD, ...) 로 직접 선언
 *   - createModel() x2 : 중복 loginId 시나리오 (같은 객체 = 같은 loginId)
 */
public class UserFixture {

    public static final String LOGIN_ID = "testuser";
    public static final String PASSWORD = "Password@1";
    public static final String NAME     = "홍길동";
    public static final String BIRTH    = "1990-01-01";
    public static final String EMAIL    = "test@loopers.com";

    public static UserModel createModel() {
        return new UserModel(LOGIN_ID, PASSWORD, NAME, BIRTH, EMAIL);
    }

    public static UserV1Dto.RegisterRequest createRequest() {
        return new UserV1Dto.RegisterRequest(LOGIN_ID, PASSWORD, NAME, BIRTH, EMAIL);
    }
}

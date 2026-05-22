# ADR-009: 좋아요 목록 소유권 검증

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`GET /api/v1/users/{userId}/likes` 요청 시, path의 `userId`가 인증된 사용자 ID와 다르면 403 Forbidden을 반환한다. 검증은 Facade 레이어에서 수행하며, 불일치 시 DB 조회를 수행하지 않는다.

## 근거

요구사항에 **"유저는 타 유저의 정보에 직접 접근할 수 없습니다"** 라고 명시되어 있다. 좋아요 목록은 개인의 소비 취향이 담긴 데이터이므로 본인만 조회할 수 있어야 한다.

### 고려한 대안

#### Option 1. 소유권 검증 없이 조회 허용

path의 `userId`로 좋아요 목록을 조회하되, 별도 소유권 검증 없이 결과를 반환하는 방식이다.

- **장점**: 구현이 단순하다. 향후 "다른 유저의 공개 좋아요 목록 보기" 기능 확장이 쉽다.
- **단점**: 요구사항을 위반한다. 개인 소비 취향 데이터가 무분별하게 노출된다. 악의적인 사용자가 타인의 좋아요 목록을 조회해 개인정보를 침해할 수 있다.

---

#### Option 2. Facade에서 소유권 검증 후 403 반환 (채택)

인증된 사용자 ID(`X-Loopers-LoginId` 헤더로 조회한 userId)와 path parameter의 `userId`를 비교하는 방식이다. 불일치 시 DB 조회 없이 즉시 403을 반환한다.

```java
// LikeFacade
public Page<ProductInfo> getLikedProducts(Long authUserId, Long pathUserId, Pageable pageable) {
    if (!authUserId.equals(pathUserId)) {
        throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
    }
    // ...
}
```

- **장점**: 요구사항을 정확히 준수한다. 불필요한 DB 조회 없이 Facade에서 즉시 차단하므로 성능상 효율적이다.
- **단점**: 향후 "공개 좋아요 목록" 기능이 추가되면 이 검증 로직을 수정해야 한다.

## 에러 응답

| 상황 | HTTP |
|---|---|
| path userId ≠ 인증된 userId | 403 Forbidden |

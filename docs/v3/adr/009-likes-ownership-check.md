# ADR-009: 좋아요 목록 소유권 검증

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`GET /api/v1/users/{userId}/likes` 요청 시, path의 `userId`가 인증된 사용자 ID와 다르면 403 Forbidden을 반환한다.

## 근거

요구사항에 "유저는 타 유저의 정보에 직접 접근할 수 없습니다"라고 명시되어 있다. 좋아요 목록은 개인의 소비 취향이 담긴 데이터이므로 본인만 조회할 수 있어야 한다.

인증된 사용자 ID는 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 식별한 후 `UserService`를 통해 조회한 `userId`를 사용한다. path parameter의 `userId`와 불일치하면 Facade에서 즉시 403을 반환하고 DB 조회를 수행하지 않는다.

## 에러 응답

| 상황 | HTTP |
|---|---|
| path userId ≠ 인증된 userId | 403 Forbidden |

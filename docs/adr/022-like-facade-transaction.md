# ADR-022: LikeFacade 좋아요 등록·취소에 @Transactional 적용

- 날짜: 2026-05-29
- 상태: 승인됨

## 결정

`LikeFacade.addLike()` 와 `LikeFacade.removeLike()` 에 `@Transactional` 을 적용한다.

```java
@Transactional
public void addLike(Long userId, Long productId) {
    likeService.like(userId, productId);
    productService.incrementLikeCount(productId);
}

@Transactional
public void removeLike(Long userId, Long productId) {
    likeService.unlike(userId, productId);
    productService.decrementLikeCount(productId);
}
```

## 배경

좋아요 등록(`addLike`)과 취소(`removeLike`)는 각각 두 Service의 쓰기 작업을 연속으로 호출한다.

| 메서드 | 호출 순서 |
|---|---|
| `addLike` | `likeService.like()` → `productService.incrementLikeCount()` |
| `removeLike` | `likeService.unlike()` → `productService.decrementLikeCount()` |

기존 구현에서는 Facade에 `@Transactional` 이 없어 두 Service 메서드가 **각각 독립된 트랜잭션**으로 실행되었다.

```
[Transaction 1] likeService.like()          → commit
[Transaction 2] productService.increment()  → commit or FAIL
```

## 문제

두 트랜잭션이 분리되어 있으면 아래 두 상황에서 `like` 등록 여부와 `likeCount` 간 **불일치**가 발생한다.

### 1. 인프라 장애

Transaction 1이 commit된 직후 DB 연결 오류나 애플리케이션 크래시가 발생하면 Transaction 2가 실행되지 않는다.

```
likeService.like() → commit ✅
                          ↑
                   (DB 연결 끊김)
productService.incrementLikeCount() → 미실행 ❌

결과: like record 존재, likeCount 미반영
```

### 2. 불일치 윈도우 (Inconsistency Window)

Transaction 1이 commit된 순간부터 Transaction 2가 완료되기까지의 짧은 시간 동안, 다른 클라이언트가 해당 상품을 조회하면 `likeCount` 가 실제보다 1 낮게 읽힌다. 트래픽이 있는 환경에서는 이 윈도우가 항상 존재한다.

## 해결

Facade에 `@Transactional` 을 추가하면 두 Service 메서드가 **동일한 트랜잭션에 참여**한다 (Spring `REQUIRED` 전파 방식 기본값).

```
[Transaction A 시작 — LikeFacade]
  ├── likeService.like()          → Transaction A에 참여
  └── productService.increment() → Transaction A에 참여
[Transaction A commit 또는 rollback]
```

하나라도 실패하면 전체가 rollback되므로 `like` 와 `likeCount` 는 항상 일관된 상태를 유지한다.

## 트랜잭션 경계 원칙과의 관계

설계 문서 section 12에 정의된 원칙:

> "여러 Service 쓰기 작업이 원자성을 요구하는 경우에 한해 Facade에서 `@Transactional` 적용"

`addLike` / `removeLike` 는 `like` 상태와 `likeCount` 가 함께 변경되어야 하는 **원자적 작업**이므로, 이 원칙에 따라 Facade에 `@Transactional` 을 적용하는 것이 맞다. `OrderFacade.createOrder()` 가 같은 이유로 Facade에 `@Transactional` 을 갖는 것과 동일한 패턴이다.

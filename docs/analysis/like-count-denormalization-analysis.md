# like_count 비정규화 설계 검토

## 발생 가능한 문제

`like_count`는 상품별 단일 row에 비정규화된 카운터 컬럼이다. `likes` 테이블에는 사용자마다 다른 row가 추가되어 서로 충돌하지 않지만, `product.like_count` 갱신은 같은 상품에 대한 요청이라면 누가 요청했든 항상 같은 row 하나를 갱신 대상으로 한다. 즉 동시 요청이 여러 row로 분산되지 않고 단일 row로 모인다. 여기에 더해 `like_count`가 `idx_product_deleted_at_like_count`, `idx_product_brand_id_deleted_at_like_count` 두 인덱스의 정렬 키이기 때문에, 값이 바뀔 때마다 단순 컬럼 갱신이 아니라 인덱스 내 위치를 옮기는 재정렬까지 필요해 한 건당 처리 비용이 늘어난다. 이로 인해 다음 상황이 발생할 수 있다.

**1. 인기 상품에 좋아요가 몰리는 경우 API 응답속도 저하**

좋아요 등록·취소는 본래 `likes` 테이블에만 영향을 주면 되는 연산이지만, `like_count`가 `product` 테이블에 비정규화되어 있어 모든 좋아요 요청이 `product` row에도 쓰기를 발생시킨다. `LikeFacade.like()/unlike()`는 좋아요 등록·취소와 `product.like_count` 갱신을 한 트랜잭션으로 묶어 처리하므로, 인덱스 2개의 재정렬 비용까지 요청 경로 안에서 처리된다. 인기 상품에 좋아요가 몰릴수록 이 비용이 중첩되어 API 응답속도가 늦어진다.

**2. 상품 정보 수정 시 좋아요 카운트 lost update 가능성**

상품 정보 수정 기능은 어드민 기능이고 race condition이 발생할 확률이 적다고 생각해 동시성 제어를 하지 않았다. `ProductService.update()`는 상품을 조회해 메모리에 적재한 뒤 `name`/`price`만 바꿔 `save()`하는데, 조회 시점과 flush 시점 사이에 다른 트랜잭션이 `like_count`를 변경하면 관리자 트랜잭션이 flush 시 자신이 들고 있던 옛 `like_count` 값으로 덮어쓴다. 그 사이에 반영된 좋아요 변경분이 사라진다.

## 개선 방향 탐색

### 집계 테이블 분리

`like_count`를 `product` 테이블에서 분리해 별도 집계 테이블(`product_stats`)로 관리하는 방안을 검토했다.

- **2번(lost update)**: 완전히 해결된다. `product` 테이블에 `like_count`가 없으므로 상품 수정 트랜잭션이 `like_count`를 덮어쓸 방법 자체가 없어진다.
- **1번(응답속도 저하)**: 해결되지 않는다. `like_count` 갱신 대상이 `product_stats` row로 바뀔 뿐, 갱신 자체는 여전히 요청 경로 안에서 동기적으로 처리된다.

### 비동기 분리

응답속도 문제를 해결하려면 `like_count` 갱신을 요청 경로 밖으로 꺼내야 한다. 비동기 애플리케이션 이벤트로 `like_count` 업데이트를 분리하면 API는 `likes` 테이블 INSERT만 하고 즉시 반환한다.

비동기 이벤트는 유실 가능성이 있으므로 Outbox 패턴으로 보완한다. 좋아요 등록·취소와 outbox 레코드 삽입을 같은 트랜잭션으로 묶고, 주기적 배치가 미처리 outbox를 이벤트로 발행해 at-least-once를 보장한다.

## 더 생각해볼 부분

비동기 분리로 API 응답속도 문제는 해결되지만, 백그라운드에서 처리되는 `product_stats.like_count` UPDATE는 여전히 같은 row로 몰린다. 인기 상품에 좋아요가 집중되는 상황에서 이 hot row로 인한 DB 부하가 실제 문제가 된다면, Kafka 같은 브로커를 도입해 두 가지를 추가로 제어할 수 있다. 같은 `product_id`의 이벤트를 모아 한 번에 반영하는 집계 처리로 DB 쓰기 횟수를 줄이고, consumer가 DB에 반영하는 속도에 상한을 두어 DB가 감당 가능한 수준 이상으로 부하가 쏟아지지 않도록 조절할 수 있다.

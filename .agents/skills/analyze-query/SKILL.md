---
name: analyze-query
description: Use when analyzing Spring @Transactional, JPA, QueryDSL, repository, or use-case query flow in this repository; especially transaction boundaries, readOnly criteria, persistence context, flush timing, lazy loading, locks, and unintended query risks.
user-invocable: true
---

# /analyze-query 트랜잭션·JPA·쿼리 흐름 분석

핵심 명제: **메서드 하나가 아니라 사용자 요청 흐름 전체에서 트랜잭션 범위와 쿼리 실행 시점을 판단한다.**

분석은 정답 단정이 아니라 현재 구조의 의도, 사용자 영향, trade-off, 개선 선택지를 드러내는 데 집중한다.

## 1. 분석 범위

다음 흐름을 기준으로 본다.

```text
Controller
  -> Facade / Application Service
  -> Domain Service
  -> Repository / QueryDSL / JPA
```

확인할 것:

- `@Transactional` 시작 지점과 `readOnly` 여부
- 쓰기 작업과 조회 작업의 순서
- Repository / QueryDSL 호출 목록
- Entity 조회인지 projection 조회인지
- 지연 로딩, flush, 변경 감지 가능성
- 반복 쿼리, N+1, lock 유지 시간
- 외부 API, 이벤트 발행, 메시지 전송 포함 여부

## 2. 사용자 관점 판단

기술적으로 가능한 분리보다 먼저 사용자에게 어떤 경험으로 보이는지 확인한다.

- 사용자는 이 요청을 하나의 행위로 인식하는가?
- 일부만 성공했을 때 사용자가 납득할 수 있는가?
- 실패 시 재시도하면 되는가, 시스템이 보상해야 하는가?
- 응답은 반드시 최신이어야 하는가, 약간의 지연을 허용할 수 있는가?
- 강한 일관성이 중요한가, 빠른 응답과 최종 일관성이 중요한가?

사용자가 하나의 행위로 인식하는 작업은 가능한 한 일관된 성공/실패 단위로 본다.

## 3. 트랜잭션 기준

조회:

- 단일 Repository 조회 + 즉시 DTO 변환은 `@Transactional` 생략 가능하다.
- 여러 Repository 조회를 조합하는 목록/상세 조회는 `@Transactional(readOnly = true)` 를 권장한다.
- 지연 로딩 가능성이 있거나 Entity graph를 탐색하는 조회도 `readOnly = true` 를 권장한다.
- 응답 전용 조회는 Entity보다 projection / bulk 조회 / read model 이 더 단순할 수 있다.

쓰기:

- 상태 변경은 명시적으로 `@Transactional` 을 둔다.
- 트랜잭션은 함께 성공하거나 함께 실패해야 하는 가장 바깥 유스케이스에 둔다.
- lock 조회 이후에는 트랜잭션을 짧고 예측 가능하게 유지한다.
- 외부 API 호출, 메시지 발행, 메일 전송은 트랜잭션 내부에 둘 이유가 있는지 먼저 의심한다.
- 상태 변경 후 추가 조회나 응답 조립이 길게 이어지면 분리 가능성을 검토한다.

## 4. JPA 실행 시점 점검

다음을 확인한다.

- 변경 감지가 의도한 Entity에만 적용되는가?
- flush가 commit 전, JPQL/QueryDSL 실행 전, 명시적 `flush()` 시점에 발생할 수 있는가?
- 읽기 전용 Entity가 불필요하게 영속성 컨텍스트에 오래 남는가?
- 지연 로딩으로 응답 변환 중 추가 쿼리가 발생할 수 있는가?
- fetch join이 pagination, 중복 row, collection loading 문제를 만들 수 있는가?

## 5. 리스크 신호

아래는 바로 수정 확정이 아니라 점검 신호로 본다.

- Controller에 `@Transactional` 이 있다.
- 단순 조회인데 쓰기 트랜잭션이 걸려 있다.
- 쓰기 트랜잭션 안에서 복잡한 조회나 대량 조회가 실행된다.
- 반복문 안에서 Repository 호출이 발생한다.
- lock을 잡은 뒤 외부 호출 또는 긴 응답 조립이 이어진다.
- 여러 조회를 조합하지만 트랜잭션이 없어 조회 시점이 갈라진다.

## 6. 보고 형식

```markdown
## 분석 대상
- 유스케이스:
- 주요 호출 흐름:

## 현재 트랜잭션 범위
- 시작 지점:
- 포함 작업:
- 실제 트랜잭션이 필요한 작업:

## 사용자 관점
- 사용자가 기대하는 완료 상태:
- 부분 성공/실패 허용 여부:
- 최신성 vs 응답 속도:

## JPA / 쿼리 실행 관점
- Entity 조회 / projection 여부:
- flush 가능 지점:
- 지연 로딩 가능성:
- 반복 쿼리 / N+1 가능성:
- lock 유지 시간:

## 판단
- 현재 유지 가능:
- 개선 후보:
- 즉시 수정 필요:

## 개선 선택지
- 선택지 A:
  - 장점:
  - 단점:
- 선택지 B:
  - 장점:
  - 단점:

## 추천
- 현재 요구사항 기준 추천:
- 트래픽/요구사항 증가 시 재검토할 지점:
```

## 7. 멈추고 질문할 때

- 사용자에게 보여야 하는 일관성 수준이 불명확할 때
- 외부 호출 실패 시 기대 동작이 정의되지 않았을 때
- 조회 결과가 같은 시점의 스냅샷이어야 하는지 불명확할 때
- 성능 문제인지, 설계 정리인지, 학습 목적 분석인지 목표가 섞여 있을 때

큰 개선점이 보여도 바로 수정하지 않는다. 먼저 리스크와 선택지를 공유하고 사용자 결정을 받는다.

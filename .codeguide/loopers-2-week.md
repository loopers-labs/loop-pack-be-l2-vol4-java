# Round 2 Legacy Reference

이 문서는 2주차 설계 제출 맥락을 보존하기 위한 참고 문서다. 현재 작업 기준은 3주차 구현 문서와 `.docs` 보조 문서다.

## 현재 사용 기준

- 2주차 제출 산출물은 `.docs/design`의 4개 설계 문서였다.
- 현재 3주차 구현은 해당 설계를 바탕으로 `catalog`, `ordering`, `payment`, `event`를 도메인 우선 구조로 구현한다.
- 2주차 설계 질문이나 PR 제출 형식은 더 이상 현재 구현의 우선 기준이 아니다.

## 남겨둘 설계 맥락

| 항목 | 현재 반영 상태 |
| --- | --- |
| 상품/브랜드 | `catalog` 모듈 구현 기준 |
| 좋아요 | `catalog.ProductLike`로 구현 |
| 주문 | `ordering` 모듈 구현 기준 |
| 재고 | `catalog.domain.product.StockService` 도메인 서비스로 구현 |
| 결제 | `payment` 모듈과 내부 worker로 구현 |
| 외부 데이터 플랫폼 | `event` 모듈 outbox/relay로 구현 |
| 포인트 | 현재 범위에서 제외 |
| 쿠폰 | 구체 API가 없어 향후 확장 포인트로만 유지 |

## 현재 기준 문서

| 목적 | 문서 |
| --- | --- |
| 현재 구현 요구사항 | `.codeguide/loopers-3-week.md` |
| 도메인 용어/상태명 | `.docs/domain.md` |
| Onion/Hexagonal/CQRS 구조 | `.docs/architecture.md` |
| 진행 상태/검증 결과 | `.docs/worklog.md` |
| API DTO 계약 | `.docs/dto-spec.md` |

## 주의

- 이 문서의 예전 제출 형식, Skill 작성 요구, PR 제목 규칙은 현재 3주차 구현 판단에 사용하지 않는다.
- `.docs/design` 문서는 설계 이력으로 남기되, 구현 판단은 최신 보조 문서와 코드 구조를 우선한다.

# Round 5 체크리스트

## 🔖 Index

- [x] 상품 목록 API에서 brandId 기반 검색, 좋아요 순 정렬 등을 처리했다
- [x] 조회 필터, 정렬 조건별 유즈케이스를 분석하여 인덱스를 적용하고 전후 성능 비교를 진행했다
  - [x] EXPLAIN 분석 및 전후 성능 비교 완료 → `05-perf-results.md` 참고
  - [x] 결정한 인덱스를 schema(import.sql)에 DDL 적용

## ❤️ Structure

- [x] 상품 목록/상세 조회 시 좋아요 수 조회 및 좋아요 순 정렬이 가능하도록 구조 개선을 진행했다
  - [x] product_like_view 분리 테이블 마이그레이션 완료 (ProductLikeViewModel, ProductLikeViewRepository)
  - [x] JPQL ad-hoc JOIN으로 likes_desc 정렬 구현 (ProductJpaRepository)
  - [x] 상품 목록 조회 시 batch fetch로 likeCount 조합 (ProductFacade)
- [x] 좋아요 적용/해제 시 상품 좋아요 수가 정상적으로 동기화되도록 진행하였다
  - [x] LikeService → product_like_view.like_count +1/-1 (비관적 락)

## 🔍 미구현 → 구현 완료

- [x] 상품 목록: `minPrice` 최소 가격 필터
- [x] 상품 목록: `maxPrice` 최대 가격 필터
- [x] 상품 목록: `inStock=true` 재고 있는 상품만 필터
- [x] 내 좋아요 목록: `sort=likes_desc` 좋아요 수 내림차순 정렬

## ⚡ Cache

- [ ] Redis 캐시를 적용하고 TTL 또는 무효화 전략을 적용했다
- [ ] 캐시 미스 상황에서도 서비스가 정상 동작하도록 처리했다

## ✍️ Technical Writing

- [ ] GitHub Issue 또는 Blog 작성 완료

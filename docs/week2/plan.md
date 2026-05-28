# Week 2 implementation Plan - 감성 이커머스

이 문서는 2주차 요구사항인 상품, 브랜드, 좋아요, 주문 기능의 TDD 기반 구현 계획을 담고 있습니다. `GEMINI.md`의 원칙을 준수하여 Red-Green-Refactor 사이클을 따릅니다.

## 0. 공통 및 기반 작업
- [x] 기초 엔티티 계층화 (ERD 분석 기반)
    - `BaseTimeEntity`: `createdAt`, `updatedAt` (Users, Stocks, Orders, ProductLikes, OrderItems)
    - `BaseSoftDeleteEntity`: `createdAt`, `updatedAt`, `isDeleted` (Brands, Products)
- [x] 각 엔티티(User, Product, Brand)에서 `id`를 직접 선언하고 적절한 기초 엔티티 상속받도록 수정 (Tidy First)
- [x] 기존 `Member` 도메인을 `User`로 변경 (Tidy First)
- [x] 기존 `ProductModel`을 Week 2 설계(ERD)에 맞게 구조 조정 (Tidy First)

## 1. 브랜드 (Brands)
- [ ] [Red] 브랜드 등록 (Admin) 테스트 작성
- [ ] [Green] 브랜드 등록 기능 구현
- [ ] [Red] 브랜드 목록 조회 (Admin) 테스트 작성
- [ ] [Green] 브랜드 목록 조회 기능 구현
- [ ] [Red] 브랜드 정보 수정 (Admin) 테스트 작성
- [ ] [Green] 브랜드 정보 수정 기능 구현
- [ ] [Red] 브랜드 삭제 (Admin, 논리 삭제) 테스트 작성
- [ ] [Green] 브랜드 삭제 기능 구현

## 2. 상품 (Products)
- [ ] [Red] 상품 등록 (Admin) 테스트 작성 (브랜드 ID 필수, 재고 분리 고려)
- [ ] [Green] 상품 등록 기능 구현
- [ ] [Red] 상품 목록 조회 (User, 필터/정렬/페이징) 테스트 작성
- [ ] [Green] 상품 목록 조회 기능 구현
- [ ] [Red] 상품 상세 조회 (User) 테스트 작성
- [ ] [Green] 상품 상세 조회 기능 구현
- [ ] [Red] 상품 정보 수정 (Admin) 테스트 작성
- [ ] [Green] 상품 정보 수정 기능 구현
- [ ] [Red] 상품 삭제 (Admin, 논리 삭제) 테스트 작성
- [ ] [Green] 상품 삭제 기능 구현
- [ ] [Red] 브랜드 삭제 시 연관 상품 연쇄 논리 삭제 테스트 작성
- [ ] [Green] 연관 상품 연쇄 논리 삭제 기능 구현

## 3. 좋아요 (Likes)
- [ ] [Red] 좋아요 등록 (Idempotent) 테스트 작성
- [ ] [Green] 좋아요 등록 및 상품 `like_count` 증가 구현
- [ ] [Red] 좋아요 취소 (Idempotent) 테스트 작성
- [ ] [Green] 좋아요 취소 및 상품 `like_count` 감소 구현
- [ ] [Red] 내가 좋아요 한 상품 목록 조회 (삭제된 상품 제외) 테스트 작성
- [ ] [Green] 내 좋아요 목록 조회 기능 구현

## 4. 주문 (Orders)
- [ ] [Red] 주문 요청 (재고 차감 포함) 테스트 작성
- [ ] [Green] 주문 요청 기능 및 재고 동시성 제어 구현
- [ ] [Red] 주문 스냅샷 저장 테스트 작성
- [ ] [Green] 주문 스냅샷 (상품명, 가격, 브랜드명) 저장 구현
- [ ] [Red] 내 주문 목록 조회 API 테스트 작성
- [ ] [Green] 내 주문 목록 조회 기능 구현
- [ ] [Red] 주문 상세 조회 API 테스트 작성
- [ ] [Green] 주문 상세 조회 기능 구현
- [ ] [Red] 전체 주문 목록 조회 (Admin) 테스트 작성
- [ ] [Green] 전체 주문 목록 조회 기능 구현

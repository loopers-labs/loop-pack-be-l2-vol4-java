# 구현 진행 보드

소스: `docs/volume-2/` (01-requirements · 02-sequence-diagrams · 03-class-diagram · 04-erd)
출력: `docs/volume-3/`
현재: PRD-1·PRD-2·PRD-3·PRD-4·LIK-3 5개 배치 commit 완료. 다음 배치(ORD-1~) 대기.

총 22개 cycle. User 도메인은 volume-1에서 완료된 정식 참조 구현이라 대상에서 제외.

| # | ID | 제목 | 도메인 | 의존성 | 단계 | 상태 |
|---|----|------|--------|--------|------|----|
| 1 | BRD-4 | 브랜드 등록 | Brand | admin 인증(신규) | commit | ✅ done |
| 2 | BRD-5 | 브랜드 수정 | Brand | BRD-4 | commit | ✅ done |
| 3 | BRD-1 | 브랜드 단건 조회(public) | Brand | BRD-4 | commit | ✅ done |
| 4 | BRD-2 | 브랜드 목록(admin) | Brand | BRD-4 | commit | ✅ done |
| 5 | BRD-3 | 브랜드 상세(admin) | Brand | BRD-4 | commit | ✅ done |
| 6 | PRD-5 | 상품 등록 | Product | Brand | commit | ✅ done |
| 7 | PRD-6 | 상품 수정 | Product | PRD-5 | commit | ✅ done |
| 8 | PRD-7 | 상품 삭제 | Product | PRD-5 | commit | ✅ done |
| 9 | BRD-6 | 브랜드 삭제(+상품 cascade) | Brand | Product(soft delete) | commit | ✅ done |
| 10 | LIK-1 | 좋아요 등록 | Like | Product, User | commit | ✅ done |
| 11 | LIK-2 | 좋아요 취소 | Like | Product, User | commit | ✅ done |
| 12 | PRD-1 | 상품 목록(public, 좋아요 수) | Product | Like(집계) | commit | ✅ done |
| 13 | PRD-2 | 상품 상세(public, 좋아요 수) | Product | Like(집계) | commit | ✅ done |
| 14 | PRD-3 | 상품 목록(admin) | Product | PRD-5 | commit | ✅ done |
| 15 | PRD-4 | 상품 상세(admin) | Product | PRD-5 | commit | ✅ done |
| 16 | LIK-3 | 좋아요한 상품 목록 | Like | PRD-1 projection | commit | ✅ done |
| 17 | ORD-1 | 단건 주문 | Order | Product, User | - | ⬜ todo |
| 18 | ORD-2 | 다중 항목 주문 | Order | ORD-1 | - | ⬜ todo |
| 19 | ORD-4 | 본인 주문 상세 | Order | ORD-1 | - | ⬜ todo |
| 20 | ORD-3 | 본인 주문 내역(날짜 범위) | Order | ORD-1 | - | ⬜ todo |
| 21 | ORD-5 | 관리자 주문 목록 | Order | ORD-1, admin 인증 | - | ⬜ todo |
| 22 | ORD-6 | 관리자 주문 상세 | Order | ORD-1, admin 인증 | - | ⬜ todo |

`단계` = 8단계(spec→plan→task→analyze→implement→test→review→commit) 중 현재 위치, `상태` = ⬜ todo / 🔄 진행 / ✅ done.

## 순서 산정 근거

- **Brand 먼저**: Product·Like·Order가 모두 브랜드를 직·간접 참조하는 최상위 aggregate.
- **도메인 내에서 쓰기→조회**: 등록/수정으로 aggregate 골격을 먼저 세우고 조회 계열을 뒤에 둔다.
- **BRD-6을 Product 뒤로(9번)**: 브랜드 삭제 시 소속 상품 cascade soft delete가 필요해 Product 도메인이 먼저 있어야 한다.
- **PRD-1·PRD-2를 Like 등록(10·11) 뒤로(12·13)**: 상품 응답의 "좋아요 수"는 좋아요 테이블 매 조회 집계(결정 1)라, 실제 좋아요 행이 생기는 LIK-1 이후에 두면 집계를 실데이터로 검증 가능.
- **LIK-3을 PRD-1 뒤로(16번)**: 좋아요한 상품 목록은 PRD-1의 상품 projection(브랜드명·가격·재고 가용여부·좋아요 수)을 재사용한다.
- **Order 마지막**: 재고 차감(Product)·회원(User) 의존. ORD-1(단건)으로 차감·스냅샷·트랜잭션 골격을 세운 뒤 ORD-2(다중) 일반화.

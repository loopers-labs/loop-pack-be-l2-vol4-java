# TDD Study Project — Claude 작업 지침

# 최우선 지시사항
모든 객체에는 반드시 테스트 코드를 작성할것,
오버레이팅 하지말것.
파일수정 및 파일삭제 마음대로 진행하지 말것.
절대 하드코딩 하지말것

# 도메인 구성
1.각 도메인 객체는 비즈니스 별로 캡슐화 되어야함.
2.도메인은 그 무엇에도 의존하지 않아야 함.
3.인터페이스의 소유권은 반드시 도메인에 있어야함.
4.항상테스트로 도메인으 순수성을검증할것

# 아키텍처 구성
1.DIP[의존성 역전 원칙] 을 준수히여 구성할것.
2.기존에 요구사항 명세서를 준수하여 구성할것.
3.추측하지 말고 프로파일링으로 병목을 측정할것.
4.측정 없는 최적화 금지.

# 패키지 구조
domain/brand, product, like, order — 엔티티 + Repository 인터페이스
application/brand, product, like, order — Service
infrastructure/brand, product, like, order — JpaRepository + Impl
test/support — FakeRepository, FakePaymentGateway

# 설계 결정사항
- status 는 String 으로 관리 (enum 미사용)
- Brand/Product 상태: DRAFT / ACTIVE / DELETED
- Order 상태: PENDING / PAID / FAILED

# 도메인 의존 관계
Brand ← Product ← Like
Brand ← Product ← OrderItem ← Order → PaymentGateway


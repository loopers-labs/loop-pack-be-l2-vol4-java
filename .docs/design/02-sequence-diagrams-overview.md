# Loopers 이커머스 — 서비스 흐름 개요

> **이 문서는 기획자·유관 부서·신규 팀원을 위한 개요 다이어그램이다.**  
> 기술 구현 상세(레이어·클래스·메서드)는 [`02-sequence-diagrams.md`](./02-sequence-diagrams.md)를 참고한다.
>
> 화살표(→) = 요청, 점선 화살표(-->) = 응답, `alt` = 분기 조건, 세로 막대 = 처리 중

---

## 전체 서비스 흐름

```
회원가입 → 도서 탐색 → 관심 도서 등록 → 주문
                        ↑
                   (관리자) 브랜드·도서 등록
```

---

## 1. 회원

### 1-1. 회원가입

```mermaid
sequenceDiagram
    actor 유저
    participant 서비스 as Loopers

    유저->>서비스: 회원가입 (아이디, 비밀번호, 이름)
    activate 서비스

    alt 이미 사용 중인 아이디
        서비스-->>유저: 실패 — 아이디 중복
    else 비밀번호 8자 미만
        서비스-->>유저: 실패 — 비밀번호 형식 오류
    end

    서비스-->>유저: 가입 완료
    deactivate 서비스
    Note over 서비스: 웰컴 쿠폰 자동 발급 예정 (기능 확장)
```

---

### 1-2. 내 정보 조회 · 비밀번호 변경

```mermaid
sequenceDiagram
    actor 유저
    participant 서비스 as Loopers

    Note over 유저,서비스: 인증이 필요한 모든 요청에 아이디·비밀번호를 함께 전달한다

    유저->>서비스: 내 정보 조회
    activate 서비스
    alt 아이디·비밀번호 불일치
        서비스-->>유저: 실패 — 인증 오류
    end
    서비스-->>유저: 내 정보 (아이디, 이름)
    deactivate 서비스

    유저->>서비스: 비밀번호 변경 (현재 비밀번호, 새 비밀번호)
    activate 서비스
    alt 현재 비밀번호 불일치
        서비스-->>유저: 실패 — 현재 비밀번호 오류
    end
    서비스-->>유저: 변경 완료
    deactivate 서비스
```

---

## 2. 도서 탐색

### 2-1. 도서 목록 · 상세 조회

```mermaid
sequenceDiagram
    actor 방문자
    participant 서비스 as Loopers

    방문자->>서비스: 도서 목록 요청
    activate 서비스
    Note over 방문자,서비스: 필터: 카테고리 (Backend / Frontend / DevOps / …), 난이도 (초급 / 중급 / 고급)<br/>정렬: 최신순 · 가격 낮은 순 · 좋아요 많은 순<br/>페이지 단위 조회 (기본 20건)
    서비스-->>방문자: 도서 목록 (이름, 저자, 카테고리, 난이도, 가격, 좋아요 수)
    deactivate 서비스

    방문자->>서비스: 도서 상세 요청
    activate 서비스
    alt 존재하지 않는 도서
        서비스-->>방문자: 실패 — 도서를 찾을 수 없음
    end
    서비스-->>방문자: 도서 상세 정보
    deactivate 서비스
    Note over 서비스: 재고 수량·ISBN은 대고객에게 숨김<br/>관리자는 전체 정보 조회 가능
```

---

## 3. 관심 도서 (좋아요)

### 3-1. 좋아요 등록 · 취소 · 목록 조회

```mermaid
sequenceDiagram
    actor 유저
    participant 서비스 as Loopers

    유저->>서비스: 도서 좋아요 등록 (로그인 필요)
    activate 서비스
    alt 존재하지 않는 도서
        서비스-->>유저: 실패 — 도서를 찾을 수 없음
    else 이미 좋아요한 도서
        서비스-->>유저: 실패 — 중복 좋아요 불가
    end
    서비스-->>유저: 등록 완료
    deactivate 서비스
    Note over 서비스: 해당 도서 좋아요 수 +1

    유저->>서비스: 도서 좋아요 취소
    activate 서비스
    alt 좋아요하지 않은 도서
        서비스-->>유저: 실패 — 좋아요 없음
    end
    서비스-->>유저: 취소 완료
    deactivate 서비스
    Note over 서비스: 해당 도서 좋아요 수 -1

    유저->>서비스: 내 좋아요 도서 목록 요청
    activate 서비스
    alt 다른 유저의 목록 접근 시도
        서비스-->>유저: 실패 — 권한 없음
    end
    서비스-->>유저: 내가 좋아요한 도서 목록
    deactivate 서비스
```

---

## 4. 주문

### 4-1. 주문 요청

```mermaid
sequenceDiagram
    actor 유저
    participant 서비스 as Loopers

    유저->>서비스: 주문 요청 (도서 목록 + 각 수량, 로그인 필요)
    activate 서비스
    Note over 유저,서비스: 예시: 도서A 2권, 도서B 1권

    alt 주문 항목이 비어있음
        서비스-->>유저: 실패 — 주문 항목 필요
    else 존재하지 않는 도서 포함
        서비스-->>유저: 실패 — 도서를 찾을 수 없음
    else 재고 부족 (한 건이라도 부족하면 전체 취소)
        서비스-->>유저: 실패 — 재고 부족
    end

    Note over 서비스: 주문 시점의 도서명·가격을 스냅샷으로 저장<br/>이후 도서 정보가 변경되어도 주문 내역은 유지<br/>재고 즉시 차감

    서비스-->>유저: 주문 완료 (주문 번호)
    deactivate 서비스
```

---

### 4-2. 주문 조회

```mermaid
sequenceDiagram
    actor 유저
    participant 서비스 as Loopers

    유저->>서비스: 주문 목록 조회 (시작일 ~ 종료일)
    activate 서비스
    Note over 서비스: 본인 주문만 조회. 최대 조회 범위 365일
    서비스-->>유저: 기간 내 주문 목록 (주문번호, 금액, 상태, 일시)
    deactivate 서비스

    유저->>서비스: 주문 상세 조회
    activate 서비스
    alt 다른 유저의 주문 접근 시도
        서비스-->>유저: 실패 — 권한 없음
    else 존재하지 않는 주문
        서비스-->>유저: 실패 — 주문을 찾을 수 없음
    end
    서비스-->>유저: 주문 상세 (도서명, 주문 시점 가격, 수량)
    deactivate 서비스
```

---

## 5. 관리자

### 5-1. 브랜드 · 도서 관리

```mermaid
sequenceDiagram
    actor 관리자
    participant 서비스 as Loopers

    Note over 관리자,서비스: 모든 관리자 요청은 내부 인증 헤더로 검증

    관리자->>서비스: 브랜드 등록 (이름)
    activate 서비스
    서비스-->>관리자: 등록 완료
    deactivate 서비스

    관리자->>서비스: 도서 등록 (브랜드, ISBN, 이름, 카테고리, 난이도, 가격, 재고)
    activate 서비스
    alt 비활성 브랜드에 등록 시도
        서비스-->>관리자: 실패 — 브랜드 비활성 상태
    else 이미 등록된 ISBN
        서비스-->>관리자: 실패 — ISBN 중복
    end
    서비스-->>관리자: 도서 등록 완료
    deactivate 서비스

    관리자->>서비스: 도서 수정 (브랜드 변경 불가)
    activate 서비스
    서비스-->>관리자: 수정 완료
    deactivate 서비스

    관리자->>서비스: 브랜드 삭제
    activate 서비스
    Note over 서비스: 소속 도서 전체 함께 삭제
    서비스-->>관리자: 삭제 완료
    deactivate 서비스
```

---

### 5-2. 전체 주문 조회

```mermaid
sequenceDiagram
    actor 관리자
    participant 서비스 as Loopers

    관리자->>서비스: 전체 주문 목록 조회 (페이지)
    activate 서비스
    Note over 서비스: 유저와 달리 모든 유저의 주문 조회 가능
    서비스-->>관리자: 전체 주문 목록
    deactivate 서비스

    관리자->>서비스: 특정 주문 상세 조회
    activate 서비스
    alt 존재하지 않는 주문
        서비스-->>관리자: 실패 — 주문 없음
    end
    서비스-->>관리자: 주문 상세 (유저 정보 포함)
    deactivate 서비스
```

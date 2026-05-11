# 도메인 모델링 가이드 (단계 6)

## 철학

이 단계는 "어떤 객체가 어떤 책임을 갖고 어떤 행위를 하는가"를 정의한다. 데이터베이스 테이블 설계가 아니라, **이 기능을 구현하는 데 필요한 역할 분담**을 정한다.

### 명시적 제약

1. **DDD 전술 패턴 중 애그리거트·도메인 이벤트는 도입 금지**. 도입 여부를 논의하지 마라.
2. **값 객체(Value Object)는 도입한다**. 각 필드의 검증 책임을 자기 자신에게 캡슐화하기 위해 값 객체를 적극 활용한다. 예: `LoginId`, `Email`, `BirthDate`, `EncryptedPassword`. (이전 버전 가이드에서 값 객체 도입 금지였으나 본 프로젝트 표준으로 도입 허용으로 변경됨.)
3. **JPA Entity = Domain Entity**: 별도 도메인 모델 레이어를 만들지 않는다. 본 단계에서 그리는 객체가 곧 JPA Entity가 된다.

## 산출 범위 (엄격)

**포함**:
- JPA Entity (예: `UserModel`) — 본 프로젝트 컨벤션에 따라 `{X}Model` 접미사.
- 값 객체 (예: `LoginId`, `Email`, `BirthDate`, `EncryptedPassword`).
- 도메인 포트 인터페이스 (예: `PasswordEncoder`).
- 어댑터 구현체 (예: `BcryptPasswordEncoder`).

**제외**:
- `Service` / `Facade` / `Repository` / `Controller` / DTO — 이들은 협력자·인프라 어댑터이지 도메인 모델이 아니다. **단계 5 시나리오 다이어그램에서 액터로 표현**하면 충분하다.
- 도메인 모델링 표는 "클래스 다이어그램"이 아니다. 객체의 책임이 **도메인 자체의 일부**인지 기준으로 선별한다.

## 출력 형식 (엄격)

4-컬럼 표 — `| 역할 (객체명) | 책임 | 속성 | 행위 |`.

### 예시 — 회원가입 도메인 (실제 산출물)

| 역할 (객체명) | 책임 | 속성 | 행위 |
|---|---|---|---|
| `UserModel` *(JPA Entity, `BaseEntity` 상속)* | 가입한 회원 한 명을 표현한다. 자신의 식별 정보와 개인 정보를 값 객체 형태로 보존한다. 비밀번호는 해시 상태로만 보관. | `id`, `loginId: LoginId`, `encryptedPassword: EncryptedPassword`, `name: String`, `birthDate: BirthDate`, `email: Email` | `new UserModel(...)` |
| `LoginId` *(값 객체)* | 로그인 ID 값을 표현. 영문/숫자 4~20자 규칙을 자기 자신이 검증. | `value: String` | `static of(String)` |
| `EncryptedPassword` *(값 객체)* | BCrypt 해시 값을 감싼다. 정적 팩토리에서 평문에 정책(8~16자·카테고리·생년월일 포함 금지)을 적용 후 해싱. | `hash: String` | `static encode(rawPassword, birthDate, encoder)`, `matches(rawPassword, encoder)` |
| `Email` *(값 객체)* | 이메일 주소. RFC 5322·254자 검증. | `value: String` | `static of(String)` |
| `BirthDate` *(값 객체)* | 생년월일. 유효 일자·미래 금지 검증. | `value: LocalDate` | `static of(LocalDate)`, `toCompact()`, `toShortCompact()` |
| `PasswordEncoder` *(포트)* | 단방향 해시 변환·검증 추상화. | — | `encode`, `matches` |
| `BcryptPasswordEncoder` *(어댑터)* | `PasswordEncoder`의 BCrypt(cost=10) 구현. | — | `encode`, `matches` 위임 |

## 작성 단계

### 1. 책임이 무엇인지 먼저 묻는다
속성 → 책임이 아니라 **책임 → 속성** 순서. "이 기능을 구현하려면 어떤 일을 누군가 해야 하는가?"부터.

### 2. 책임 위치를 결정한다
- 데이터에 가까운 행위는 그 데이터를 가진 객체에.
- 단일 필드 값의 검증(포맷·길이·범위)은 해당 값 객체에 캡슐화.
- 평문 비밀번호 같은 민감 정보는 값 객체(`EncryptedPassword`) 안에서 검증·해싱하고 외부엔 해시만 노출.

### 3. 행위는 동사로
`encode(rawPassword)`, `matches(rawPassword)`, `static of(String)`.

### 4. 값 객체 = 자기 검증 + 불변
- 생성자/정적 팩토리에서 검증 → 위반 시 `CoreException(BAD_REQUEST, "...")`.
- 불변 (setter 없음).
- 동등성은 값 기반 (`record` 사용 시 자동).

## 흔한 결정 포인트

- **여러 필드를 함께 검증해야 하는 경우** (예: 비밀번호에 생년월일 포함 금지) → 어느 값 객체가 둘 다 받아 검증하는 게 자연스러운지 결정. 회원가입 예시: `EncryptedPassword.encode(rawPassword, birthDate, encoder)`.
- **표현 변환은 도메인의 책임인가, DTO의 책임인가?** 마스킹·통화 표시 같은 표현 계층 관심사는 응답 DTO로 분리.
- **PK는 어디에?** `BaseEntity.id` (Long)을 상속받아 사용.
- **상태(status) enum**: 단순 라벨이면 enum, 행위가 크게 갈리면 별도 값 객체.

## 단계 7-b로의 매핑

- 표의 JPA Entity → 한 테이블
- 값 객체의 단일 `value` 속성 → 컬럼 1개로 풀어쓰기 (`LoginId.value` → `login_id`)
- 복합 값 객체(예: 주소: 시·구·번지) → `@Embeddable` + 여러 컬럼
- 포트/어댑터는 ERD에 등장하지 않음 (Spring Bean으로 등록)

## 폐기된 산출물

다음 보조 표는 **만들지 마라**:

- **검증 책임 분포 표** — 책임은 객체 표의 4번째 컬럼(행위)에 자연스럽게 드러난다.
- **협력 관계 도식·표** — 단계 5 시나리오 다이어그램이 협력을 보여주므로 중복 X.
- **에러 코드 매핑 표** — 단계 7-a API 명세 에러 매트릭스로 일원화.

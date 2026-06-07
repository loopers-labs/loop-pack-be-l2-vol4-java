# 네이밍 컨벤션

## 책임
코드 전 레이어에서 일관된 이름을 강제한다. 가독성·검색성·의도 전달을 위한 규칙 모음.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/LoginId.java`
`apps/commerce-api/src/main/java/com/loopers/domain/user/PasswordEncrypter.java`
`apps/commerce-api/src/main/java/com/loopers/interfaces/api/user/UserV1Controller.java`

## 핵심 규칙

### 변수명
- 의미를 담은 풀네임을 쓴다. 축약형(`enc`, `pwd`, `result`, `data`)은 사용하지 않는다.
- 올바른 예: `encryptedPassword`, `matchingResult`, `signUpRequest`, `authenticatedUser`.
- 미가공 입력값 파라미터에는 `raw` 접두사를 붙여 VO 변환 전 값임을 드러낸다: `rawLoginId`, `rawPassword`, `rawBirthDate`.
- 예외: 루프 변수 `i`, catch 절 `e` — 스코프가 한 줄인 관행적 단일 문자만 허용.

### 클래스 접미사 규약 (레이어별)
| 레이어 | 접미사 | 예 |
|---|---|---|
| 도메인 VO | (접미사·접두사 없음, 도메인 개념명) | `Name`, `Price`, `Stock`, `LoginId`, `Email` (`UserName`·`BrandName` 금지) |
| 도메인 엔티티 | `*Model` | `UserModel` |
| 도메인 서비스 | `*Service` | `UserService` |
| 도메인 Repository 인터페이스 | `*Repository` | `UserRepository` |
| 인프라 구현체 | `*RepositoryImpl` | `UserRepositoryImpl` |
| 인프라 JPA | `*JpaRepository` | `UserJpaRepository` |
| Application Facade | `*Facade` | `UserFacade` |
| Application 출력 DTO | `*Info` | `UserSignUpInfo`, `UserMyInfo` |
| 인터페이스 Controller | `*V1Controller` | `UserV1Controller` |
| 인터페이스 DTO | `*V1Dto` | `UserV1Dto` |
| 인터페이스 API 명세 | `*V1ApiSpec` | `UserV1ApiSpec` |

### 메서드 어휘 강도 (도메인 모델)
도메인 모델의 메서드는 행위 의미가 강한 동사형을 피하고 상태·값을 묘사하는 형태로 쓴다.

- boolean 반환: `matches*`, `is*`, `has*` 접두사. 예: `matchesPassword(rawPassword, passwordEncrypter)`.
- 값 반환: `*Value` 등 명사형 접미사. 예: `maskedValue()`.
- `authenticate`, `mask` 같은 강한 행위 동사는 표현·인프라 계층(ArgumentResolver 등)의 몫이므로 도메인 모델에서 회피.
- Repository 조회는 `find*`(없을 수 있어 `Optional` 반환)와 `get*`(존재 보장 — 없으면 `CoreException(NOT_FOUND)`)으로 가른다. 예: `findActiveByLoginId`(Optional) vs `getActiveById`(엔티티 또는 NOT_FOUND). soft delete 도메인은 살아있는 행만 거른다는 의미로 `*Active*`를 이름에 담는다. 존재 보장이 필요한 Facade는 `get*`을 직접 호출하고 별도 `mustFind*` 헬퍼를 두지 않는다.

### CRUD 메서드 어휘 (Controller·Facade)
Controller와 Facade의 CRUD 유스케이스 메서드는 `create*`/`read*`/`update*`/`delete*` 동사로 통일한다.

- 조회는 `get*`이 아니라 `read*`를 쓴다. 예: `readProduct`, `readProducts`.
- 생성 `create*`, 수정 `update*`, 삭제 `delete*`.
- 위 4개 동사가 CRUD 표준 어휘이며, 표현·유스케이스 계층 전반에서 일관되게 적용한다.

### 정적 팩토리 메서드 (Effective Java)
- 매개변수 **하나** → `from(X)`. 예: `LoginId.from("kyle123")`, `Email.from("kyle@example.com")`.
- 매개변수 **여러 개** → `of(X, Y, ...)`. 예: `UserModel.of(loginId, name, email, ...)`.
- 위는 기본 규칙이다. 도메인 의미가 분명하고 그 의미를 드러내는 게 더 적절하면 의미 있는 동사형 팩토리도 허용한다. 예: `EncryptedPassword.encrypt(rawPassword, passwordEncrypter)`는 암호화 의미가 핵심이라 `from`/`of` 대신 동사형을 택한다.

### 용어 통일 — 암호화 계열
- 인터페이스: `PasswordEncrypter`
- 구현체: `BcryptPasswordEncrypter`
- 메서드: `encrypt(rawPassword)`, `matches(rawPassword, encryptedPassword)`
- 도메인 VO: `EncryptedPassword`
- "포트", "어댑터", "헥사고날" 같은 아키텍처 패턴 용어는 이름에 드러내지 않는다.

## 핵심 발췌
```java
// PasswordEncrypter — 인터페이스명·메서드명 규약
public interface PasswordEncrypter {
    String encrypt(String rawPassword);
    boolean matches(String rawPassword, String encryptedPassword);
}

// 도메인 모델 — boolean 메서드 어휘
public boolean matchesPassword(String rawPassword, PasswordEncrypter passwordEncrypter) {
    return passwordEncrypter.matches(rawPassword, this.encryptedPassword.value());
}

// 정적 팩토리 — from / of
public static LoginId from(String value) { ... }
public static UserModel of(LoginId loginId, Name name, ...) { ... }
```

## do / don't
- ✅ 변수명은 풀네임: `encryptedPassword`, `matchingResult`.
- ✅ 클래스 접미사는 레이어 규약 그대로: `*Model`, `*Service`, `*Facade`, `*V1Controller` 등.
- ✅ boolean 도메인 메서드는 `matches*`/`is*`/`has*`.
- ✅ Controller·Facade CRUD 메서드는 `create*`/`read*`/`update*`/`delete*`. 조회는 `read*`.
- ✅ 정적 팩토리는 매개변수 하나 `from`, 여럿 `of`. 의미가 분명하면 동사형(`encrypt`)도 허용.
- ❌ 축약형(`enc`, `pwd`, `usr`) 변수명 사용하지 않는다.
- ❌ 도메인 모델에 `authenticate`, `mask` 같은 강한 행위 동사를 두지 않는다.
- ❌ Controller·Facade의 조회 메서드를 `get*`으로 짓지 않는다 — `read*`를 쓴다.
- ❌ 암호화 계열 클래스·메서드에 "Port", "Adapter" 같은 아키텍처 용어를 붙이지 않는다.

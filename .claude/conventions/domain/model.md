# Model (Aggregate Root Entity) 컨벤션

## 책임
도메인 aggregate root 엔티티. 자기 상태의 불변식을 메서드로 보호하고, VO를 통해 값 규칙을 캡슐화한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/UserModel.java`

## 핵심 규칙
- `@Entity` + `extends BaseEntity`로 선언한다. `BaseEntity`가 `id`(IDENTITY 전략), `createdAt`, `updatedAt`, `deletedAt`을 자동 관리한다.
- `@Table`에 `uniqueConstraints`를 명시해 DB 제약을 문서화한다.
- 검증·형식 규칙·도메인 행위가 있는 값은 VO(`@Embeddable` record)로 캡슐화해 `@Embedded` 필드로 보유한다. 검증·행위가 전혀 없는 단순 값(예: 자유 서술 설명)은 VO로 감싸지 않고 원시 타입 + `@Column`으로 모델에 직접 둔다 — 행위 없는 래퍼는 과설계다. (예: `BrandModel.name`은 `BrandName` VO, `BrandModel.description`은 `String`)
- `@NoArgsConstructor(access = PROTECTED)` + `@AllArgsConstructor(access = PROTECTED)`. 외부에서 직접 생성자를 호출할 수 없게 막는다.
- 생성자에서 VO 조립·검증(`from()` / `encrypt()` / 교차 불변식)이 필요한 모델은 `private @Builder` 생성자로 선언한다 — 빌더 내부에서 VO를 조립하므로 빌더를 통과한 인스턴스는 항상 유효하다(예: `UserModel`, `OrderItemModel`). 반대로 생성 시 조립·검증 없이 값을 그대로 주입하는 모델은 클래스 레벨 `@Builder`를 허용한다 — 검증 없는 `private` 생성자 빌더는 잉여다(예: `LikeModel`, `OrderModel`).
- 도메인 행위는 메서드로 표현하고, 불변식 위반 시 `CoreException`을 던진다.
- 여러 VO에 걸친 교차 불변식(예: 비밀번호에 생년월일 포함 금지)은 단일 VO가 아니라 모델의 `private` 헬퍼에 두고, 빌더와 변경 메서드(`changePassword`)가 공유한다.
- 메서드 어휘: boolean 반환은 `matches*`/`is*`/`has*`(예: `matchesPassword`), 값 반환은 명사형 접미사(`*Value`; 예시는 `Name` VO의 `maskedValue()`). `authenticate`·`mask` 같은 강한 행위 동사는 표현/인프라 계층 몫이므로 도메인 모델에서 회피.

## 핵심 발췌
```java
@Getter
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class UserModel extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Embedded
    private EncryptedPassword encryptedPassword;

    // ... 나머지 @Embedded VO 필드 ...

    @Builder
    private UserModel(String rawLoginId, String rawPassword, ..., PasswordEncrypter passwordEncrypter) {
        this.loginId = LoginId.from(rawLoginId);
        this.birthDate = BirthDate.from(rawBirthDate);
        this.encryptedPassword = encryptRawPassword(rawPassword, this.birthDate, passwordEncrypter);
        // ...
    }

    private static EncryptedPassword encryptRawPassword(String rawPassword, BirthDate birthDate, PasswordEncrypter passwordEncrypter) {
        if (birthDate.isContainedIn(rawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        return EncryptedPassword.encrypt(rawPassword, passwordEncrypter);
    }

    public boolean matchesPassword(String rawPassword, PasswordEncrypter passwordEncrypter) {
        return encryptedPassword.matches(rawPassword, passwordEncrypter);
    }

    public void changePassword(String currentRawPassword, String newRawPassword, PasswordEncrypter passwordEncrypter) {
        if (!matchesPassword(currentRawPassword, passwordEncrypter)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 일치하지 않습니다.");
        }
        // ... 추가 불변식 검사 후 갱신
    }
}
```

## do / don't
- ✅ 불변식을 모델 메서드 안에 둔다.
- ✅ 생성자에서 VO 조립·검증이 필요하면 빌더를 `private` 생성자에 붙여 생성 경로를 단일화한다. 조립·검증이 없으면 클래스 레벨 `@Builder`도 무방하다.
- ✅ 검증·행위 있는 값만 VO로 감싼다. 없는 단순 값은 원시 타입 + `@Column`으로 직접 둔다.
- ❌ 세터로 상태를 외부에서 직접 바꾸지 않는다.
- ❌ 표현 계층에 모델을 노출하지 않는다 — Facade가 `Info`로 변환한 뒤 반환한다.
- ❌ 검증·행위 없는 값을 VO로 감싸지 않는다(과설계).

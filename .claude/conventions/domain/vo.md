# VO (Value Object) 컨벤션

## 책임
도메인 값을 캡슐화하고 스스로 유효성을 보장하는 불변 객체. 형식·길이·null 검증의 단일 진실의 원천.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/LoginId.java` — 표준 VO. `Email`, `Name`, `BirthDate`, `EncryptedPassword`도 같은 패턴.

## 핵심 규칙
- `record` + `@Embeddable`로 선언하고, 엔티티에 `@Embedded`로 합성된다.
- 컴포넌트에 `@Column(name=..., nullable=false, length=...)`로 영속 매핑을 단다.
- 생성은 정적 팩토리로만. 매개변수 하나면 `from(X)`, 여러 개면 `of(...)`. 도메인 의미가 분명하면 의미 있는 동사형 팩토리도 허용한다(예: `EncryptedPassword.encrypt(...)`). public 생성자는 노출하지 않는다.
- 검증은 전적으로 `from()`(또는 동사형 팩토리)이 책임진다 — null/blank, 길이 경계, 패턴, 값 범위(예: `BirthDate`의 미래 날짜 금지). 위반 시 `CoreException(ErrorType.BAD_REQUEST, 메시지)`.
- 임계값(MIN/MAX, 패턴)은 `private static final` 상수로 추출한다.
- 검증 사유가 둘 이상이면 메시지를 사유별로 분리한다(길이 위반 vs 형식 위반).
- 변수가 들어가는 메시지는 `String.format("...%d~%d...", MIN, MAX)`로 작성한다.
- 값 접근자는 명사형(record 컴포넌트명, 예: `value()`). boolean 인상을 주는 형용사형은 회피.
- 컴포넌트 타입은 `String`에 한정되지 않는다 — `BirthDate(LocalDate value)`처럼 도메인에 맞는 타입을 쓴다.
- 값 검증 외에 표현·계산용 파생 메서드를 둘 수 있다(예: `Name.maskedValue()`, `BirthDate`의 포맷 변환). 메서드 어휘는 `common/naming.md`를 따른다.

## 핵심 발췌
```java
@Embeddable
public record LoginId(
    @Column(name = "login_id", nullable = false, length = 20) 
    String value
) {
    
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9]+");

    public static LoginId from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("로그인 ID는 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }
        
        if (!ALLOWED_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문 및 숫자만 허용됩니다.");
        }
        
        return new LoginId(value);
    }
}
```

## do / don't
- ✅ 검증을 VO `from()`에 모은다.
- ❌ 컨트롤러 DTO에 Bean Validation(`@NotBlank` 등)을 중복으로 두지 않는다 (DRY 위반, 검증 단일화).
- ❌ public 생성자/세터를 노출하지 않는다.

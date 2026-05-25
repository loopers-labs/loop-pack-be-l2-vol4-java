# Info 컨벤션

## 책임
application 계층이 표현 계층으로 넘기는 출력 DTO. 도메인 모델에서 표현에 필요한 최소 필드만 추출해 표현 계층이 엔티티에 의존하지 않도록 단절한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/application/user/UserSignUpInfo.java`,
`apps/commerce-api/src/main/java/com/loopers/application/user/UserMyInfo.java`

## 핵심 규칙
- `record`로 선언한다. 불변이고 자동 생성된 accessor를 그대로 쓴다.
- 변환은 `from(XxxModel)` 정적 팩토리 하나로만 만든다. 이 메서드가 모델→Info 변환의 단일 진실의 원천이 된다.
- 노출 필드는 표현에 필요한 최소 집합이다. 민감 정보(암호화된 비밀번호 등)나 관리자 전용 필드는 포함하지 않는다.
- VO 값을 꺼낼 때는 `.value()`(또는 해당 VO의 accessor)를 호출한다. 반환 타입은 `String`뿐 아니라 `LocalDate` 같은 표준 타입일 수 있다(예: `UserMyInfo`의 `birthDate`). VO 자체를 필드로 들고 표현 계층까지 전달하지 않는다.
- VO에 도메인 전용 메서드(예: `Name.maskedValue()`)가 있으면 Info 생성 시점에 호출해 이미 표현용으로 가공된 값을 담는다.
- 같은 모델이라도 유스케이스별로 노출 필드가 다르면 Info를 분리한다(`UserSignUpInfo` 가입 결과 vs `UserMyInfo` 조회 결과).

## 핵심 발췌
```java
public record UserSignUpInfo(Long userId, String loginId) {

    public static UserSignUpInfo from(UserModel user) {
        return new UserSignUpInfo(user.getId(), user.getLoginId().value());
    }
}

public record UserMyInfo(String loginId, String name, LocalDate birthDate, String email) {

    public static UserMyInfo from(UserModel user) {
        return new UserMyInfo(
            user.getLoginId().value(),
            user.getName().maskedValue(),
            user.getBirthDate().value(),
            user.getEmail().value()
        );
    }
}
```

## do / don't
- ✅ `from(XxxModel)` 단일 팩토리로 변환을 일원화한다.
- ✅ VO는 `.value()` 또는 전용 accessor로 원시값을 꺼낸다.
- ❌ 민감 정보(암호화된 비밀번호 등)를 Info 필드에 포함하지 않는다.
- ❌ Info가 엔티티(`*Model`)를 필드로 들고 표현 계층까지 전달하지 않는다 — 계층 결합·지연 로딩 위험.
- ❌ 여러 변환 경로(생성자 직접 호출 등)를 분산시키지 않는다.

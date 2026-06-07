# Info 컨벤션

## 책임
application 계층이 표현 계층으로 넘기는 출력 DTO. 도메인 모델에서 표현에 필요한 최소 필드만 추출해 표현 계층이 엔티티에 의존하지 않도록 단절한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/application/user/UserSignUpInfo.java`,
`apps/commerce-api/src/main/java/com/loopers/application/user/UserMyInfo.java`,
`apps/commerce-api/src/main/java/com/loopers/application/brand/BrandInfo.java`

## 핵심 규칙
- `record`로 선언한다. 불변이고 자동 생성된 accessor를 그대로 쓴다.
- 변환은 `from(XxxModel)` 정적 팩토리 하나로만 만든다. 이 메서드가 모델→Info 변환의 단일 진실의 원천이 된다.
- 노출 필드는 표현에 필요한 최소 집합이다. 민감 정보(암호화된 비밀번호 등)나 관리자 전용 필드는 포함하지 않는다.
- VO 값을 꺼낼 때는 `.value()`(또는 해당 VO의 accessor)를 호출한다. 반환 타입은 `String`뿐 아니라 `LocalDate` 같은 표준 타입일 수 있다(예: `UserMyInfo`의 `birthDate`). VO 자체를 필드로 들고 표현 계층까지 전달하지 않는다.
- VO에 도메인 전용 메서드(예: `Name.maskedValue()`)가 있으면 Info 생성 시점에 호출해 이미 표현용으로 가공된 값을 담는다.
- 같은 모델이라도 유스케이스별로 노출 필드가 다르면 Info를 분리한다(`UserSignUpInfo` 가입 결과 vs `UserMyInfo` 조회 결과).
- 목록·페이지 응답에서도 `Info`는 단건 record 그대로 둔다. 컬렉션 변환은 Facade가 `List`/`Page`에 `.map(Info::from)`을 적용해 `List<Info>`/`Page<Info>`로 만든다(`application/facade.md`). `Info`에 컬렉션 래퍼나 페이지 메타(`totalPages` 등)를 담지 않는다 — 그건 표현 계층 `*V1Dto.PageResponse`의 책임이다.
- **필드는 참조형으로 통일한다**(`Long`·`Integer`·`Boolean`·`String` 등). 조회로 항상 채워져 null이 안 나는 값이라도 원시형(`int`/`long`/`boolean`)을 섞지 않는다 — 기존 `*Info`(BrandInfo·UserMyInfo)와 일관성 유지. 같은 기준을 표현 계층 응답 record(`*V1Dto`)에도 적용한다.
- 변환 원천은 `*Model`뿐 아니라 **도메인 read-model projection**(`domain.<domain>.projection`)일 수 있다(조회 유스케이스). `from(ProductSummary)`처럼 projection을 받아도 무방하다 — 표현 계층을 엔티티로부터 단절한다는 목적은 동일하게 충족된다.

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

// 목록·페이지 — Info는 단건 record, 컬렉션 변환은 Facade가 .map(Info::from)으로
public record BrandInfo(Long brandId, String name, String description, ZonedDateTime createdAt, ZonedDateTime updatedAt) {

    public static BrandInfo from(BrandModel brand) {
        return new BrandInfo(brand.getId(), brand.getName().value(), brand.getDescription(),
            brand.getCreatedAt(), brand.getUpdatedAt());
    }
}
Page<BrandInfo> brands = brandRepository.findActiveByPage(page, size).map(BrandInfo::from);  // Facade
```

## do / don't
- ✅ `from(XxxModel)` 단일 팩토리로 변환을 일원화한다.
- ✅ VO는 `.value()` 또는 전용 accessor로 원시값을 꺼낸다.
- ✅ 목록·페이지는 Facade의 `.map(Info::from)`으로 `List<Info>`/`Page<Info>`를 만들고, `Info`는 단건 record로 유지한다.
- ❌ 민감 정보(암호화된 비밀번호 등)를 Info 필드에 포함하지 않는다.
- ❌ Info가 엔티티(`*Model`)를 필드로 들고 표현 계층까지 전달하지 않는다 — 계층 결합·지연 로딩 위험.
- ❌ 여러 변환 경로(생성자 직접 호출 등)를 분산시키지 않는다.

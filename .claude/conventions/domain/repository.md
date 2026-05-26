# Repository (Domain Interface) 컨벤션

## 책임
도메인이 요구하는 영속 연산을 선언하는 추상 인터페이스. 구현(JPA 연동)은 `infrastructure` 레이어가 담당하며, 도메인은 이 인터페이스에만 의존한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/UserRepository.java`

## 핵심 규칙
- 순수 인터페이스로 선언한다. 프레임워크 어노테이션(`@Repository` 등) 없음.
- 메서드 이름은 도메인 어휘로 작성한다(`save`, `getActiveById`, `findActiveByLoginId`, `existsByLoginId`, `existsByEmail`).
- **`find*` vs `get*` 구분**: 없을 수 있는 조회는 `find*`로 `Optional<T>`을 반환하고, 존재가 보장돼야 하는 조회는 `get*`로 엔티티를 반환하되 없으면 `CoreException(ErrorType.NOT_FOUND)`을 던진다(예: `getActiveById`). 존재 여부 확인은 `boolean`(`exists*`).
  - "없으면 예외" 정책을 Repository가 책임지므로, Facade는 별도 `mustFind*` 헬퍼를 두지 않고 `get*`을 직접 호출한다. ("없으면 예외" 계약을 이름에 담는다.)
  - 예외를 던지는 `get*`의 구현·동작은 `RepositoryImpl`이 가지며 통합 테스트(Testcontainers)로 검증한다.
- **soft delete 필터링은 이름에 담는다**: `deletedAt IS NULL`만 조회하는 연산은 `*Active*`로 명시한다(예: `getActiveById`, `findActiveByLoginId`, `existsActiveByName`). 활성 필터링은 숨은 동작이 아니라 메서드 이름으로 드러낸다. 단, DB 전체 컬럼 unique 제약이 걸린 중복 검사(`existsByLoginId`/`existsByEmail`)는 전체 행을 대상으로 두어 제약과 정합을 맞춘다.
- 도메인 패키지(`domain.<domain>`)에 위치한다. `infrastructure` 패키지에 두지 않는다.
- 도메인이 이 인터페이스에만 의존하므로, JPA·DB 구현체를 교체해도 도메인 코드는 변경되지 않는다.

## 핵심 발췌
```java
public interface UserRepository {

    UserModel save(UserModel user);

    UserModel getActiveById(Long id);              // 활성 회원만, 없으면 CoreException(NOT_FOUND)

    Optional<UserModel> findActiveByLoginId(String loginId);  // 활성 회원만, 없을 수 있음

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
```

## do / don't
- ✅ 도메인 패키지 안에 인터페이스를 둔다.
- ✅ 메서드 이름을 도메인 어휘로 유지한다.
- ✅ 존재 보장 조회는 `get*`(없으면 NOT_FOUND), 없을 수 있는 조회는 `find*`(Optional)로 가른다.
- ❌ Facade에 `mustFind*` 같은 존재 보장 헬퍼를 두지 않는다 — Repository `get*`이 그 책임을 갖는다.
- ❌ 이 인터페이스에 `JpaRepository` 상속을 추가하지 않는다 (그건 `infrastructure`의 `JpaRepository` 역할).
- ❌ `@Query`·파생 쿼리 어노테이션을 여기에 두지 않는다.

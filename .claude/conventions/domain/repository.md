# Repository (Domain Interface) 컨벤션

## 책임
도메인이 요구하는 영속 연산을 선언하는 추상 인터페이스. 구현(JPA 연동)은 `infrastructure` 레이어가 담당하며, 도메인은 이 인터페이스에만 의존한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/domain/user/UserRepository.java`

## 핵심 규칙
- 순수 인터페이스로 선언한다. 프레임워크 어노테이션(`@Repository` 등) 없음.
- 메서드 이름은 도메인 어휘로 작성한다(`save`, `findById`, `findByLoginId`, `existsByLoginId`, `existsByEmail`).
- 반환 타입은 단일 엔티티이면 `Optional<T>`, 존재 여부이면 `boolean`으로 한다.
- 도메인 패키지(`domain.<domain>`)에 위치한다. `infrastructure` 패키지에 두지 않는다.
- 도메인이 이 인터페이스에만 의존하므로, JPA·DB 구현체를 교체해도 도메인 코드는 변경되지 않는다.

## 핵심 발췌
```java
public interface UserRepository {

    UserModel save(UserModel user);

    Optional<UserModel> findById(Long id);

    Optional<UserModel> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
```

## do / don't
- ✅ 도메인 패키지 안에 인터페이스를 둔다.
- ✅ 메서드 이름을 도메인 어휘로 유지한다.
- ❌ 이 인터페이스에 `JpaRepository` 상속을 추가하지 않는다 (그건 `infrastructure`의 `JpaRepository` 역할).
- ❌ `@Query`·파생 쿼리 어노테이션을 여기에 두지 않는다.

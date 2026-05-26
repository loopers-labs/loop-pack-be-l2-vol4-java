# Facade 컨벤션

## 책임
유스케이스를 조합하는 application 계층의 오케스트레이터. 도메인 `Repository`로 객체를 조회하고, 엔티티 메서드·도메인 서비스에 협력을 위임하며, 트랜잭션 경계를 가진다. 도메인 모델을 application 출력 DTO(`*Info`)로 변환해 표현 계층으로부터 엔티티를 보호한다.

## 정식 참조
`apps/commerce-api/src/main/java/com/loopers/application/user/UserFacade.java`,
`apps/commerce-api/src/main/java/com/loopers/application/brand/BrandFacade.java`(조회·페이징)

## 핵심 규칙
- `@Service` + 클래스 레벨 `@Transactional`로 선언한다. 조회 전용 유스케이스 메서드는 `@Transactional(readOnly = true)`로 오버라이드한다.
- 도메인 `Repository` 인터페이스와 도메인 서비스(`@Component`)를 주입한다(`@RequiredArgsConstructor`).
- 유스케이스 흐름: Repository로 도메인 객체 조회 → 엔티티 메서드/도메인 서비스에 협력 위임 → Repository로 저장 → `Info`로 변환해 반환.
- 존재 보장 조회는 Repository의 `get*`(예: `getActiveById`)를 호출한다 — 없을 때의 `CoreException(NOT_FOUND)`은 `RepositoryImpl`이 던진다(`infrastructure/repositoryImpl.md`). Facade에 `mustFind*` 헬퍼를 두지 않는다.
- 중복/충돌 검사는 Repository의 `exists*`로 하고, 충돌이면 Facade가 `CoreException(CONFLICT)`을 던진다.
- 페이징 유스케이스는 `page/size`를 Facade에서 가드한다(범위 위반 시 `CoreException(BAD_REQUEST)`). 쿼리파라미터에 `@Validated`를 쓰지 않는다(ApiSpec 인터페이스+JDK 프록시로 500). Repository가 돌려준 `Page<Model>`을 `page.map(Info::from)`으로 변환한다.
- 입력은 raw 파라미터(또는 application 입력 객체), 출력은 `*Info`. 도메인 모델을 파라미터로 받거나 그대로 반환하지 않는다(표현계층 보호).

## 핵심 발췌
```java
@Service
@Transactional
@RequiredArgsConstructor
public class BrandFacade {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final BrandRepository brandRepository;

    public BrandCreateInfo createBrand(String name, String description) {
        if (brandRepository.existsActiveByName(name)) {                 // 충돌 검사 → CONFLICT
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }
        BrandModel newBrand = BrandModel.builder().rawName(name).rawDescription(description).build();
        return BrandCreateInfo.from(brandRepository.save(newBrand));
    }

    @Transactional(readOnly = true)
    public BrandInfo readBrand(Long brandId) {
        return BrandInfo.from(brandRepository.getActiveById(brandId));   // get* — 없으면 RepositoryImpl이 NOT_FOUND
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> readBrands(int page, int size) {
        if (page < 0 || size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {  // page/size 가드 → BAD_REQUEST
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("페이지 번호는 0 이상, 크기는 %d~%d만 허용됩니다.", MIN_PAGE_SIZE, MAX_PAGE_SIZE));
        }
        return brandRepository.findActiveByPage(page, size).map(BrandInfo::from);  // Page<Model> → Page<Info>
    }
}
```

## do / don't
- ✅ Facade가 트랜잭션 경계·Repository 접근을 갖고, 도메인 객체를 조회해 도메인 서비스/엔티티에 위임한다.
- ✅ 조회 유스케이스는 `@Transactional(readOnly = true)`.
- ✅ 존재 보장은 Repository `get*` 호출(없을 때 NOT_FOUND는 RepositoryImpl 책임), 중복 충돌은 `exists*` 후 `CONFLICT`.
- ✅ 페이징은 page/size를 Facade가 가드(`BAD_REQUEST`)하고 `Page.map(Info::from)`으로 변환한다.
- ✅ 출력은 `Info.from(model)`로 변환해 반환한다.
- ❌ Facade에 `mustFind*` 헬퍼나 NOT_FOUND 변환을 두지 않는다 — Repository `get*`가 담당.
- ❌ Facade에 도메인 규칙(불변식·계산)을 직접 두지 않는다 — 엔티티/VO/도메인 서비스에 위임.
- ❌ 도메인 모델을 파라미터로 받거나 그대로 반환하지 않는다.

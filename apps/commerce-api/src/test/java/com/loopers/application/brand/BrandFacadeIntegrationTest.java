package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BrandFacadeIntegrationTest {

    private final BrandFacade brandFacade;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandFacadeIntegrationTest(
        BrandFacade brandFacade,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.brandFacade = brandFacade;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Create {

        @DisplayName("이름과 설명이 유효하면, 브랜드가 저장되고 응답에 id 가 포함된다.")
        @Test
        void persistsBrand_whenInputIsValid() {
            // given
            String name = "나이키";
            String description = "Just Do It";

            // when
            BrandAdminInfo result = brandFacade.create(name, description);

            // then
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo(name),
                () -> assertThat(result.description()).isEqualTo(description),
                () -> assertThat(brandJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("이름이 이미 존재하면, DUPLICATE_BRAND_NAME 예외를 던지고 두 번째 row 는 저장되지 않는다.")
        @Test
        void throwsDuplicateBrandName_whenNameAlreadyExists() {
            // given
            String name = "나이키";
            brandFacade.create(name, "Just Do It");

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.create(name, "다른 설명"));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_BRAND_NAME),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(1)
            );
        }
    }

    @DisplayName("고객이 브랜드를 조회할 때, ")
    @Nested
    class GetForCustomer {

        @DisplayName("존재하는 active 브랜드 id 로 조회하면, BrandInfo 가 반환된다.")
        @Test
        void returnsBrandInfo_whenBrandExistsAndIsActive() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            BrandInfo result = brandFacade.getForCustomer(created.id());

            // then
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("Just Do It")
            );
        }

        @DisplayName("존재하지 않는 id 로 조회하면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenBrandDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.getForCustomer(missingId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("soft-delete 된 브랜드 id 로 조회하면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.getForCustomer(created.id()));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("어드민이 브랜드 목록을 조회할 때, ")
    @Nested
    class List_ {

        @DisplayName("등록된 모든 브랜드가 페이지 메타와 함께 반환된다.")
        @Test
        void returnsPagedBrands_withMeta() {
            // given
            brandFacade.create("나이키", "Just Do It");
            brandFacade.create("아디다스", "Impossible is Nothing");
            brandFacade.create("푸마", "Forever Faster");

            // when
            Page<BrandAdminInfo> page = brandFacade.list(PageRequest.of(0, 20));

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).extracting(BrandAdminInfo::name)
                    .containsExactlyInAnyOrder("나이키", "아디다스", "푸마")
            );
        }

        @DisplayName("soft-delete 된 브랜드도 목록에 포함되고, deletedAt 이 노출된다.")
        @Test
        void includesSoftDeletedBrand_withDeletedAt() {
            // given
            BrandAdminInfo deleted = brandFacade.create("폐기", "deprecated");
            brandFacade.create("나이키", "Just Do It");
            BrandModel toDelete = brandJpaRepository.findById(deleted.id()).orElseThrow();
            toDelete.delete();
            brandJpaRepository.save(toDelete);

            // when
            Page<BrandAdminInfo> page = brandFacade.list(PageRequest.of(0, 20));

            // then
            BrandAdminInfo deletedInfo = page.getContent().stream()
                .filter(info -> info.id().equals(deleted.id()))
                .findFirst()
                .orElseThrow();
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(2),
                () -> assertThat(deletedInfo.deletedAt()).isNotNull()
            );
        }

        @DisplayName("page=0, size=2 로 조회하면, 최대 2건만 반환되고 totalPages 가 계산된다.")
        @Test
        void respectsPageAndSize() {
            // given
            for (int i = 1; i <= 5; i++) {
                brandFacade.create("브랜드" + i, "설명" + i);
            }

            // when
            Page<BrandAdminInfo> page = brandFacade.list(PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getTotalElements()).isEqualTo(5),
                () -> assertThat(page.getTotalPages()).isEqualTo(3)
            );
        }
    }

    @DisplayName("어드민이 브랜드 단건을 조회할 때, ")
    @Nested
    class GetForAdmin {

        @DisplayName("활성 브랜드를 조회하면, deletedAt 이 null 인 BrandAdminInfo 가 반환된다.")
        @Test
        void returnsAdminInfo_whenBrandIsActive() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            BrandAdminInfo result = brandFacade.getForAdmin(created.id());

            // then
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("Just Do It"),
                () -> assertThat(result.deletedAt()).isNull()
            );
        }

        @DisplayName("soft-delete 된 브랜드도 조회되고, deletedAt 이 노출된다.")
        @Test
        void returnsAdminInfo_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            BrandAdminInfo result = brandFacade.getForAdmin(created.id());

            // then
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 id 로 조회하면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenBrandDoesNotExist() {
            // given
            Long missingId = 9_999L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.getForAdmin(missingId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("어드민이 브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("이름과 설명을 모두 새 값으로 전달하면, 둘 다 변경된 정보가 반환된다.")
        @Test
        void updatesNameAndDescription_whenBothAreGiven() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            BrandAdminInfo result = brandFacade.update(created.id(), "아디다스", "Impossible is Nothing");

            // then
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.name()).isEqualTo("아디다스"),
                () -> assertThat(result.description()).isEqualTo("Impossible is Nothing")
            );
        }

        @DisplayName("설명만 전달되면, 설명만 변경되고 이름은 유지된다.")
        @Test
        void updatesOnlyDescription_whenNameIsNull() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            BrandAdminInfo result = brandFacade.update(created.id(), null, "새 슬로건");

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("새 슬로건")
            );
        }

        @DisplayName("새 이름이 다른 브랜드와 중복되면, DUPLICATE_BRAND_NAME 예외를 던지고 변경되지 않는다.")
        @Test
        void throwsDuplicateBrandName_whenNewNameClashesWithAnotherBrand() {
            // given
            brandFacade.create("나이키", "Just Do It");
            BrandAdminInfo target = brandFacade.create("아디다스", "Impossible is Nothing");

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.update(target.id(), "나이키", null));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_BRAND_NAME),
                () -> assertThat(brandJpaRepository.findById(target.id()).orElseThrow().getName())
                    .isEqualTo("아디다스")
            );
        }

        @DisplayName("자기 자신의 현재 이름과 같은 값으로 수정해도, 정상 처리된다.")
        @Test
        void doesNotThrow_whenNewNameIsSameAsItsOwn() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            BrandAdminInfo result = brandFacade.update(created.id(), "나이키", "새 슬로건");

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("새 슬로건")
            );
        }

        @DisplayName("soft-delete 된 브랜드를 수정하려고 하면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenBrandIsSoftDeleted() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");
            BrandModel brand = brandJpaRepository.findById(created.id()).orElseThrow();
            brand.delete();
            brandJpaRepository.save(brand);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.update(created.id(), "아디다스", null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("이름이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenNewNameIsBlank() {
            // given
            BrandAdminInfo created = brandFacade.create("나이키", "Just Do It");

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandFacade.update(created.id(), "   ", null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

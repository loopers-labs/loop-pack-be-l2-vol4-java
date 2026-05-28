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
            BrandInfo result = brandFacade.create(name, description);

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
            BrandInfo created = brandFacade.create("나이키", "Just Do It");

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
            BrandInfo created = brandFacade.create("나이키", "Just Do It");
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
}

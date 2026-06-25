package com.loopers.application.brand;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.LikeEntity;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class BrandApplicationServiceIntegrationTest {

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @SpyBean
    private ProductRepository productRepository;

    @SpyBean
    private InventoryRepository inventoryRepository;

    @SpyBean
    private LikeRepository likeRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        Mockito.reset(productRepository, inventoryRepository, likeRepository);
        databaseCleanUp.truncateAllTables();
    }

    // ─────────────────────────────────────────────
    // getBrand — Customer 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("브랜드 단건 조회 (Customer)")
    @Nested
    class GetBrand {

        @DisplayName("[ECP] 존재하는 brandId로 조회하면 BrandInfo를 반환한다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            BrandInfo created = brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act
            BrandInfo result = brandApplicationService.getBrand(created.id());

            // assert
            assertAll(
                    () -> assertNotNull(result.id()),
                    () -> assertEquals("나이키", result.name()),
                    () -> assertEquals("스포츠 브랜드", result.description())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 brandId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandApplicationService.getBrand("999"));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // createBrand — Admin 브랜드 등록
    // ─────────────────────────────────────────────

    @DisplayName("브랜드 등록 (Admin)")
    @Nested
    class CreateBrand {

        @DisplayName("[ECP] 유효한 name과 description으로 등록하면 id가 할당된 BrandInfo를 반환한다.")
        @Test
        void returnsBrandInfo_whenRequestIsValid() {
            // act
            BrandInfo result = brandApplicationService.createBrand("아디다스", "독일 스포츠 브랜드");

            // assert
            assertAll(
                    () -> assertNotNull(result.id()),
                    () -> assertEquals("아디다스", result.name()),
                    () -> assertEquals("독일 스포츠 브랜드", result.description())
            );
        }

        @DisplayName("[ECP] 이미 존재하는 name으로 등록하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandApplicationService.createBrand("나이키", "다른 설명"));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getBrands — Admin 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("브랜드 목록 조회 (Admin)")
    @Nested
    class GetBrands {

        @DisplayName("[ECP] 등록된 브랜드 수만큼 목록이 반환된다.")
        @Test
        void returnsBrandPage_withAllBrands() {
            // arrange
            brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            brandApplicationService.createBrand("아디다스", "독일 스포츠 브랜드");

            // act
            Page<BrandInfo> result = brandApplicationService.getBrands(PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }

        @DisplayName("[BVA] page=0, size=1이면 첫 번째 브랜드 1건만 반환된다.")
        @Test
        void returnsOnlyOneBrand_whenPageSizeIsOne() {
            // arrange
            brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            brandApplicationService.createBrand("아디다스", "독일 스포츠 브랜드");

            // act
            Page<BrandInfo> result = brandApplicationService.getBrands(PageRequest.of(0, 1));

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertEquals(1, result.getContent().size())
            );
        }
    }

    // ─────────────────────────────────────────────
    // updateBrand — Admin 수정
    // ─────────────────────────────────────────────

    @DisplayName("브랜드 수정 (Admin) — Decision Table: 존재여부 × name 중복 여부")
    @Nested
    class UpdateBrand {

        @DisplayName("[Decision Table] 브랜드가 존재하고 name이 유일하면 수정된다.")
        @Test
        void updatesBrand_whenBrandExistsAndNameIsUnique() {
            // arrange
            BrandInfo created = brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act
            BrandInfo result = brandApplicationService.updateBrand(created.id(), "나이키 코리아", "한국 스포츠 브랜드");

            // assert
            assertAll(
                    () -> assertEquals("나이키 코리아", result.name()),
                    () -> assertEquals("한국 스포츠 브랜드", result.description())
            );
        }

        @DisplayName("[Decision Table] 존재하지 않는 brandId이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandApplicationService.updateBrand("999", "나이키", "스포츠 브랜드"));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 다른 브랜드와 name이 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameBelongsToAnotherBrand() {
            // arrange
            brandApplicationService.createBrand("아디다스", "독일 스포츠 브랜드");
            BrandInfo target = brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandApplicationService.updateBrand(target.id(), "아디다스", "새 설명"));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 자신과 동일한 name으로 수정하면 description만 변경된다.")
        @Test
        void updatesBrand_whenNameIsSameAsCurrentBrand() {
            // arrange
            BrandInfo created = brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act
            BrandInfo result = brandApplicationService.updateBrand(created.id(), "나이키", "새로운 설명");

            // assert
            assertAll(
                    () -> assertEquals("나이키", result.name()),
                    () -> assertEquals("새로운 설명", result.description())
            );
        }
    }

    // ─────────────────────────────────────────────
    // deleteBrand — Admin 삭제 + 연쇄 Soft Delete
    // ─────────────────────────────────────────────

    @DisplayName("브랜드 삭제 (Admin)")
    @Nested
    class DeleteBrand {

        @DisplayName("[State Transition] 브랜드 삭제 후 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_afterBrandIsDeleted() {
            // arrange
            BrandInfo created = brandApplicationService.createBrand("나이키", "스포츠 브랜드");

            // act
            brandApplicationService.deleteBrand(created.id());

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandApplicationService.getBrand(created.id()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 브랜드 삭제 시 연관된 상품, 재고, 좋아요가 연쇄 Soft Delete된다.")
        @Test
        void cascadeSoftDeletes_relatedProductsInventoriesAndLikes() {
            // arrange
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, 10);
            likeRepository.save(new LikeEntity("1", product.id()));

            // act
            brandApplicationService.deleteBrand(brand.id());

            // assert — soft delete: deletedAt IS NOT NULL
            assertThat(productJpaRepository.findById(product.id()))
                    .isPresent()
                    .get()
                    .satisfies(p -> assertNotNull(p.getDeletedAt()));

            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(product.id()))
                    .isEmpty();

            assertThat(likeJpaRepository.findByUserIdAndProductIdAndDeletedAtIsNull("1", product.id()))
                    .isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // 트랜잭션 원자성
    // ─────────────────────────────────────────────

    @DisplayName("트랜잭션 원자성")
    @Nested
    class TransactionalAtomicity {

        @DisplayName("[Transactional] deleteBrand 중 product deleteAll 실패 시 brand 삭제가 롤백된다.")
        @Test
        void rollbacksBrandDelete_whenProductDeleteAllFails() {
            // arrange
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, 10);
            doThrow(new RuntimeException("강제 실패")).when(productRepository).findAllByIds(anyList());

            // act
            assertThrows(RuntimeException.class, () -> brandApplicationService.deleteBrand(brand.id()));

            // assert: brand가 롤백되어 여전히 조회 가능
            assertDoesNotThrow(() -> brandApplicationService.getBrand(brand.id()));
        }

        @DisplayName("[Transactional] deleteBrand 중 inventory deleteAllByProducts 실패 시 brand와 product 삭제가 롤백된다.")
        @Test
        void rollbacksBrandAndProductDelete_whenInventoryDeleteAllFails() {
            // arrange
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, 10);
            doThrow(new RuntimeException("강제 실패")).when(inventoryRepository).deleteAllByProductIds(anyList());

            // act
            assertThrows(RuntimeException.class, () -> brandApplicationService.deleteBrand(brand.id()));

            // assert: brand와 product 모두 롤백
            assertDoesNotThrow(() -> brandApplicationService.getBrand(brand.id()));
            assertThat(productJpaRepository.findById(product.id()))
                    .isPresent()
                    .get()
                    .satisfies(p -> assertNull(p.getDeletedAt()));
        }

        @DisplayName("[Transactional] deleteBrand 중 like deleteAllByProducts 실패 시 brand, product, inventory 삭제가 롤백된다.")
        @Test
        void rollbacksBrandProductAndInventoryDelete_whenLikeDeleteAllFails() {
            // arrange
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, 10);
            doThrow(new RuntimeException("강제 실패")).when(likeRepository).deleteAllByProductIds(anyList());

            // act
            assertThrows(RuntimeException.class, () -> brandApplicationService.deleteBrand(brand.id()));

            // assert: brand, product, inventory 모두 롤백
            assertDoesNotThrow(() -> brandApplicationService.getBrand(brand.id()));
            assertThat(productJpaRepository.findById(product.id()))
                    .isPresent()
                    .get()
                    .satisfies(p -> assertNull(p.getDeletedAt()));
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(product.id()))
                    .isPresent();
        }
    }
}

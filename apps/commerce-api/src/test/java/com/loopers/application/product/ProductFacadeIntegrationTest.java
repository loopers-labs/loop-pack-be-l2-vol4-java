package com.loopers.application.product;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private LikeService likeService;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // ─────────────────────────────────────────────
    // createProduct — Admin 상품 등록
    // ─────────────────────────────────────────────

    @DisplayName("상품 등록 (Admin)")
    @Nested
    class CreateProduct {

        @DisplayName("[ECP] 유효한 요청으로 등록하면 id가 할당된 ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenRequestIsValid() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");

            // act
            ProductInfo result = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // assert
            assertAll(
                    () -> assertNotNull(result.id()),
                    () -> assertEquals(brand.id(), result.brandId()),
                    () -> assertEquals("나이키", result.brandName()),
                    () -> assertEquals("에어맥스", result.name()),
                    () -> assertEquals("운동화 설명", result.description()),
                    () -> assertEquals(100_000L, result.price()),
                    () -> assertEquals(10, result.quantity())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 brandId로 등록하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productFacade.createProduct(999L, "에어맥스", "운동화 설명", 100_000L, 10));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 상품 등록 시 재고(Inventory)도 함께 생성된다.")
        @Test
        void createsInventory_whenProductIsCreated() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");

            // act
            ProductInfo result = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // assert
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(result.id()))
                    .isPresent()
                    .get()
                    .satisfies(inv -> assertEquals(10, inv.getQuantity()));
        }
    }

    // ─────────────────────────────────────────────
    // getProduct — Customer 상품 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("상품 단건 조회 (Customer)")
    @Nested
    class GetProduct {

        @DisplayName("[ECP] 존재하는 productId로 조회하면 brandName과 quantity를 포함한 ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo created = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 5);

            // act
            ProductInfo result = productFacade.getProduct(created.id());

            // assert
            assertAll(
                    () -> assertNotNull(result.id()),
                    () -> assertEquals("나이키", result.brandName()),
                    () -> assertEquals(5, result.quantity())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 productId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productFacade.getProduct(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getAllProducts — Customer 상품 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("상품 목록 조회 (Customer)")
    @Nested
    class GetAllProducts {

        @DisplayName("[ECP] brandId 없이 조회하면 전체 상품 목록이 반환된다.")
        @Test
        void returnsAllProducts_whenNoBrandIdGiven() {
            // arrange
            BrandInfo brand1 = brandFacade.createBrand("나이키", "스포츠 브랜드");
            BrandInfo brand2 = brandFacade.createBrand("아디다스", "독일 브랜드");
            productFacade.createProduct(brand1.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            productFacade.createProduct(brand2.id(), "울트라부스트", "운동화 설명", 120_000L, 5);

            // act
            Page<ProductInfo> result = productFacade.getAllProducts(null, PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }

        @DisplayName("[ECP] brandId를 지정하면 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdGiven() {
            // arrange
            BrandInfo brand1 = brandFacade.createBrand("나이키", "스포츠 브랜드");
            BrandInfo brand2 = brandFacade.createBrand("아디다스", "독일 브랜드");
            productFacade.createProduct(brand1.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            productFacade.createProduct(brand2.id(), "울트라부스트", "운동화 설명", 120_000L, 5);

            // act
            Page<ProductInfo> result = productFacade.getAllProducts(brand1.id(), PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals("에어맥스", result.getContent().get(0).name())
            );
        }
    }

    // ─────────────────────────────────────────────
    // updateProduct — Admin 상품 수정
    // ─────────────────────────────────────────────

    @DisplayName("상품 수정 (Admin)")
    @Nested
    class UpdateProduct {

        @DisplayName("[ECP] 유효한 요청으로 수정하면 변경된 ProductInfo를 반환한다.")
        @Test
        void returnsUpdatedProductInfo_whenRequestIsValid() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo created = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // act
            ProductInfo result = productFacade.updateProduct(created.id(), "에어포스", "새 설명", 90_000L, 20);

            // assert
            assertAll(
                    () -> assertEquals("에어포스", result.name()),
                    () -> assertEquals("새 설명", result.description()),
                    () -> assertEquals(90_000L, result.price()),
                    () -> assertEquals(20, result.quantity())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 productId로 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productFacade.updateProduct(999L, "에어포스", "새 설명", 90_000L, 20));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // deleteProduct — Admin 상품 삭제 + 연쇄 Soft Delete
    // ─────────────────────────────────────────────

    @DisplayName("상품 삭제 (Admin)")
    @Nested
    class DeleteProduct {

        @DisplayName("[State Transition] 상품 삭제 후 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_afterProductIsDeleted() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo created = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);

            // act
            productFacade.deleteProduct(created.id());

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productFacade.getProduct(created.id()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 상품 삭제 시 연관된 재고와 좋아요가 연쇄 Soft Delete된다.")
        @Test
        void cascadeSoftDeletes_inventoryAndLikes() {
            // arrange
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo created = productFacade.createProduct(brand.id(), "에어맥스", "운동화 설명", 100_000L, 10);
            likeService.like(1L, created.id());

            // act
            productFacade.deleteProduct(created.id());

            // assert
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(created.id()))
                    .isEmpty();

            assertThat(likeJpaRepository.findByUserIdAndProductIdAndDeletedAtIsNull(1L, created.id()))
                    .isEmpty();
        }
    }
}

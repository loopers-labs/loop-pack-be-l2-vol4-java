package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long BRAND_ID = 1L;
    private static final Long OTHER_BRAND_ID = 2L;
    private static final String PRODUCT_NAME = "에어맥스";
    private static final String PRODUCT_DESCRIPTION = "나이키 런닝화";
    private static final Long PRODUCT_PRICE = 150000L;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductEntity productOf(Long id, Long brandId, String name, String desc, Long price) {
        return ProductEntity.of(id, brandId, name, desc, price, 0L, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    private ProductEntity defaultProduct(Long id) {
        return productOf(id, BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);
    }

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 값으로 생성하면 id가 할당된 상품이 생성된다.")
        @Test
        void createsProduct_whenRequestIsValid() {
            // arrange
            ProductEntity saved = defaultProduct(1L);
            given(productRepository.save(any())).willReturn(saved);

            // act
            ProductEntity result = productService.createProduct(BRAND_ID, PRODUCT_NAME, PRODUCT_DESCRIPTION, PRODUCT_PRICE);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(BRAND_ID, result.getBrandId()),
                    () -> assertEquals(PRODUCT_NAME, result.getName()),
                    () -> assertEquals(PRODUCT_PRICE, result.getPrice())
            );
            verify(productRepository).save(any());
        }
    }

    @DisplayName("상품 단건 조회")
    @Nested
    class GetProduct {

        @DisplayName("[ECP] 존재하는 id로 조회하면 ProductEntity를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            ProductEntity existing = defaultProduct(1L);
            given(productRepository.find(1L)).willReturn(Optional.of(existing));

            // act
            ProductEntity result = productService.getProduct(1L);

            // assert
            assertAll(
                    () -> assertEquals(1L, result.getId()),
                    () -> assertEquals(PRODUCT_NAME, result.getName())
            );
            verify(productRepository).find(1L);
        }

        @DisplayName("[ECP] 존재하지 않는 id로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(productRepository.find(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.getProduct(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("상품 목록 조회")
    @Nested
    class GetAllProducts {

        @DisplayName("[ECP] brandId 없이 조회하면 전체 상품 페이지가 반환된다.")
        @Test
        void returnsAllProducts_whenNoBrandIdFilter() {
            // arrange
            List<ProductEntity> products = List.of(
                    defaultProduct(1L),
                    productOf(2L, OTHER_BRAND_ID, "에어포스", "나이키 스니커즈", 130000L)
            );
            PageRequest pageable = PageRequest.of(0, 20);
            given(productRepository.findAll(null, pageable)).willReturn(new PageImpl<>(products, pageable, 2));

            // act
            Page<ProductEntity> result = productService.getAllProducts(null, pageable);

            // assert
            assertEquals(2, result.getTotalElements());
            verify(productRepository).findAll(null, pageable);
        }

        @DisplayName("[ECP] brandId로 필터링하면 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // arrange
            List<ProductEntity> filtered = List.of(defaultProduct(1L));
            PageRequest pageable = PageRequest.of(0, 20);
            given(productRepository.findAll(BRAND_ID, pageable)).willReturn(new PageImpl<>(filtered, pageable, 1));

            // act
            Page<ProductEntity> result = productService.getAllProducts(BRAND_ID, pageable);

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals(BRAND_ID, result.getContent().get(0).getBrandId())
            );
            verify(productRepository).findAll(BRAND_ID, pageable);
        }
    }

    @DisplayName("브랜드별 상품 ID 목록 조회")
    @Nested
    class FindIdsByBrand {

        @DisplayName("[ECP] brandId로 조회하면 해당 브랜드의 상품 id 목록이 반환된다.")
        @Test
        void returnsProductIds_whenBrandExists() {
            // arrange
            given(productRepository.findIdsByBrandId(BRAND_ID)).willReturn(List.of(1L, 2L));

            // act
            List<Long> ids = productService.findIdsByBrand(BRAND_ID);

            // assert
            assertAll(
                    () -> assertEquals(2, ids.size()),
                    () -> assertTrue(ids.contains(1L)),
                    () -> assertTrue(ids.contains(2L))
            );
            verify(productRepository).findIdsByBrandId(BRAND_ID);
        }
    }

    @DisplayName("상품 일괄 삭제")
    @Nested
    class DeleteAll {

        @DisplayName("[State Transition] 상품 id 목록으로 일괄 삭제하면 모든 엔티티가 soft delete 상태로 변경된다.")
        @Test
        void deletesAllProducts_whenIdsProvided() {
            // arrange
            ProductEntity p1 = defaultProduct(1L);
            ProductEntity p2 = defaultProduct(2L);
            given(productRepository.findAllByIds(List.of(1L, 2L))).willReturn(List.of(p1, p2));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            productService.deleteAll(List.of(1L, 2L));

            // assert
            assertAll(
                    () -> assertTrue(p1.isDeleted()),
                    () -> assertTrue(p2.isDeleted())
            );
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class UpdateProduct {

        @DisplayName("[Decision Table] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(productRepository.find(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.updateProduct(999L, "변경명", "변경설명", 200000L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 존재하는 상품이면 수정된다.")
        @Test
        void updatesProduct_whenProductExists() {
            // arrange
            ProductEntity existing = defaultProduct(1L);
            given(productRepository.find(1L)).willReturn(Optional.of(existing));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            ProductEntity result = productService.updateProduct(1L, "에어포스", "나이키 스니커즈", 130000L);

            // assert
            assertAll(
                    () -> assertEquals("에어포스", result.getName()),
                    () -> assertEquals("나이키 스니커즈", result.getDescription()),
                    () -> assertEquals(130000L, result.getPrice())
            );
        }
    }

    @DisplayName("상품 삭제 — State Transition: Active → Deleted")
    @Nested
    class DeleteProduct {

        @DisplayName("[State Transition] 존재하는 상품을 삭제하면 엔티티가 soft delete 상태로 저장된다.")
        @Test
        void deletesProduct_whenExists() {
            // arrange
            ProductEntity existing = defaultProduct(1L);
            given(productRepository.find(1L)).willReturn(Optional.of(existing));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            productService.deleteProduct(1L);

            // assert
            assertTrue(existing.isDeleted());
            verify(productRepository).save(existing);
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(productRepository.find(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.deleteProduct(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("좋아요 수 증가")
    @Nested
    class IncrementLikeCount {

        @DisplayName("[ECP] 존재하는 상품이면 likeCount 증가 요청이 전달된다.")
        @Test
        void incrementsLikeCount_whenProductExists() {
            // arrange
            given(productRepository.find(1L)).willReturn(Optional.of(defaultProduct(1L)));

            // act
            productService.incrementLikeCount(1L);

            // assert
            verify(productRepository).find(1L);
            verify(productRepository).incrementLikeCount(1L);
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(productRepository.find(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.incrementLikeCount(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("좋아요 수 감소")
    @Nested
    class DecrementLikeCount {

        @DisplayName("[ECP] 존재하는 상품이면 likeCount 감소 요청이 전달된다.")
        @Test
        void decrementsLikeCount_whenProductExists() {
            // arrange
            given(productRepository.find(1L)).willReturn(Optional.of(defaultProduct(1L)));

            // act
            productService.decrementLikeCount(1L);

            // assert
            verify(productRepository).find(1L);
            verify(productRepository).decrementLikeCount(1L);
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            given(productRepository.find(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.decrementLikeCount(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}

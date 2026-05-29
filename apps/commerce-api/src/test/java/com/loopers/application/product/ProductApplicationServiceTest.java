package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@Tag("domain")
class ProductApplicationServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final LikeRepository likeRepository = mock(LikeRepository.class);
    private final ProductDomainService productDomainService = new ProductDomainService();
    private final ProductApplicationService productApplicationService =
        new ProductApplicationService(productRepository, brandRepository, likeRepository, productDomainService);

    private static final Long PRODUCT_ID = 1L;
    private static final Long BRAND_ID = 1L;
    private static final String VALID_NAME = "나이키 에어맥스";
    private static final String VALID_DESCRIPTION = "편안한 운동화";
    private static final Long VALID_PRICE = 100_000L;
    private static final Integer VALID_STOCK = 10;

    private ProductModel stubProduct() {
        return new ProductModel(PRODUCT_ID, BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK, null, null);
    }

    private BrandModel stubBrand() {
        return new BrandModel(BRAND_ID, "나이키", "스포츠 브랜드", null, null);
    }

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    class GetProducts {

        @DisplayName("브랜드명과 좋아요 수를 조합한 ProductInfo 목록을 반환한다.")
        @Test
        void returnsComposedProductInfoList() {
            // arrange
            when(productRepository.findAll(any(), any(Pageable.class))).thenReturn(List.of(stubProduct()));
            when(brandRepository.findAllByIds(anyCollection())).thenReturn(Map.of(BRAND_ID, stubBrand()));
            when(likeRepository.countByProductIds(anyCollection())).thenReturn(Map.of(PRODUCT_ID, 7L));

            // act
            List<ProductInfo> result = productApplicationService.getProducts(null, 0, 20, null);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).name()).isEqualTo(VALID_NAME),
                () -> assertThat(result.get(0).brandName()).isEqualTo("나이키"),
                () -> assertThat(result.get(0).likeCount()).isEqualTo(7L)
            );
        }

        @DisplayName("likes_desc 정렬이면 findAllOrderByLikeCountDesc를 호출한다.")
        @Test
        void usesLikeCountSort_whenLikesDesc() {
            // arrange
            when(productRepository.findAllOrderByLikeCountDesc(any(), any(Pageable.class))).thenReturn(List.of(stubProduct()));
            when(brandRepository.findAllByIds(anyCollection())).thenReturn(Map.of(BRAND_ID, stubBrand()));
            when(likeRepository.countByProductIds(anyCollection())).thenReturn(Map.of());

            // act
            List<ProductInfo> result = productApplicationService.getProducts(null, 0, 20, "likes_desc");

            // assert
            assertThat(result).hasSize(1);
            verify(productRepository).findAllOrderByLikeCountDesc(any(), any(Pageable.class));
            verify(productRepository, never()).findAll(any(), any(Pageable.class));
        }
    }

    @DisplayName("상품 단건 조회 시, ")
    @Nested
    class GetProduct {

        @DisplayName("상품/브랜드/좋아요수를 조합한 ProductInfo를 반환한다.")
        @Test
        void returnsComposedProductInfo_whenProductExists() {
            // arrange
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(stubProduct()));
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(stubBrand()));
            when(likeRepository.countByProductId(PRODUCT_ID)).thenReturn(3L);

            // act
            ProductInfo result = productApplicationService.getProduct(PRODUCT_ID);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(PRODUCT_ID),
                () -> assertThat(result.name()).isEqualTo(VALID_NAME),
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.likeCount()).isEqualTo(3L)
            );
        }

        @DisplayName("상품이 없으면 CoreException(NOT_FOUND)이 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> productApplicationService.getProduct(PRODUCT_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 생성 시, ")
    @Nested
    class CreateProduct {

        @DisplayName("입력값으로 ProductModel을 생성하고 save()를 호출한 뒤 ProductAdminInfo를 반환한다.")
        @Test
        void savesProduct_andReturnsAdminInfo() {
            // arrange
            when(productRepository.save(any(ProductModel.class))).thenReturn(stubProduct());

            // act
            ProductAdminInfo result = productApplicationService.createProduct(BRAND_ID, VALID_NAME, VALID_DESCRIPTION, VALID_PRICE, VALID_STOCK);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo(VALID_NAME),
                () -> assertThat(result.price()).isEqualTo(VALID_PRICE)
            );
            verify(productRepository).save(any(ProductModel.class));
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    class UpdateProduct {

        @DisplayName("상품이 존재하면 update() 후 save()를 호출한다.")
        @Test
        void callsSave_afterUpdate_whenProductExists() {
            // arrange
            ProductModel product = stubProduct();
            ProductModel updated = new ProductModel(PRODUCT_ID, BRAND_ID, "아디다스 부스트", "러닝화", 120_000L, 5, null, null);
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(updated);

            // act
            ProductAdminInfo result = productApplicationService.updateProduct(PRODUCT_ID, "아디다스 부스트", "러닝화", 120_000L, 5);

            // assert
            verify(productRepository).save(product);
            assertThat(result.name()).isEqualTo("아디다스 부스트");
        }

        @DisplayName("상품이 없으면 CoreException이 발생하고 save()가 호출되지 않는다.")
        @Test
        void throwsNotFound_andDoesNotCallSave_whenProductDoesNotExist() {
            // arrange
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> productApplicationService.updateProduct(PRODUCT_ID, "아디다스 부스트", "러닝화", 120_000L, 5));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productRepository, never()).save(any());
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    class DeleteProduct {

        @DisplayName("상품이 존재하면 delete(id)를 호출한다.")
        @Test
        void callsDelete_whenProductExists() {
            // arrange
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.of(stubProduct()));

            // act
            productApplicationService.deleteProduct(PRODUCT_ID);

            // assert
            verify(productRepository).delete(PRODUCT_ID);
        }

        @DisplayName("상품이 없으면 CoreException이 발생하고 delete(id)가 호출되지 않는다.")
        @Test
        void throwsNotFound_andDoesNotCallDelete_whenProductDoesNotExist() {
            // arrange
            when(productRepository.find(PRODUCT_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> productApplicationService.deleteProduct(PRODUCT_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productRepository, never()).delete(any());
        }
    }
}

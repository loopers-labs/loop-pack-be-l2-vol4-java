package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.projection.ProductAdminView;
import com.loopers.domain.product.projection.ProductDetail;
import com.loopers.domain.product.projection.ProductSummary;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("상품을 생성할 때,")
    @Nested
    class CreateProduct {

        private final Long brandId = 1L;
        private final String name = "감성 가디건";
        private final String description = "포근한 감성 가디건";
        private final Integer price = 39_000;
        private final Integer stock = 50;

        @DisplayName("브랜드가 활성 상태로 존재하면 상품을 저장하고 생성 정보를 반환한다.")
        @Test
        void returnsCreateInfo_whenBrandIsActive() {
            // arrange
            given(brandRepository.existsActiveById(brandId)).willReturn(true);
            given(productRepository.save(any(ProductModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            ProductCreateInfo createInfo = productFacade.createProduct(brandId, name, description, price, stock);

            // assert
            assertAll(
                () -> assertThat(createInfo).isNotNull(),
                () -> then(brandRepository).should().existsActiveById(brandId),
                () -> then(productRepository).should().save(any(ProductModel.class))
            );
        }

        @DisplayName("브랜드가 활성 상태로 존재하지 않으면 NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsNotFound_whenBrandIsAbsent() {
            // arrange
            given(brandRepository.existsActiveById(brandId)).willReturn(false);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> productFacade.createProduct(brandId, name, description, price, stock))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(productRepository).should(never()).save(any(ProductModel.class))
            );
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        private final Long productId = 1L;
        private final String name = "리뉴얼 가디건";
        private final String description = "새 설명";
        private final Integer price = 42_000;
        private final Integer stock = 30;

        @DisplayName("대상 상품이 활성 상태로 존재하면 값을 갱신하고 수정 정보를 반환한다.")
        @Test
        void returnsUpdateInfo_whenProductIsActive() {
            // arrange
            ProductModel product = ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            given(productRepository.getActiveById(productId)).willReturn(product);

            // act
            ProductUpdateInfo updateInfo = productFacade.updateProduct(productId, name, description, price, stock);

            // assert
            assertAll(
                () -> assertThat(updateInfo).isNotNull(),
                () -> assertThat(product.getName().value()).isEqualTo(name),
                () -> assertThat(product.getPrice().value()).isEqualTo(price),
                () -> assertThat(product.getStock().value()).isEqualTo(stock),
                () -> then(productRepository).should().getActiveById(productId)
            );
        }

        @DisplayName("대상 상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(productRepository.getActiveById(productId))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.updateProduct(productId, name, description, price, stock))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        private final Long productId = 1L;

        @DisplayName("대상 상품이 활성 상태로 존재하면 삭제 처리한다.")
        @Test
        void deletesProduct_whenProductIsActive() {
            // arrange
            ProductModel product = ProductModel.builder()
                .brandId(1L)
                .rawName("감성 가디건")
                .rawDescription("포근한 감성 가디건")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            given(productRepository.findActiveById(productId)).willReturn(Optional.of(product));

            // act
            productFacade.deleteProduct(productId);

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("대상 상품이 없거나 이미 삭제되었으면 예외 없이 아무 동작도 하지 않는다(멱등).")
        @Test
        void doesNothing_whenProductIsAbsent() {
            // arrange
            given(productRepository.findActiveById(productId)).willReturn(Optional.empty());

            // act
            productFacade.deleteProduct(productId);

            // assert
            then(productRepository).should().findActiveById(productId);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class ReadProducts {

        @DisplayName("정상 요청이면 조회 결과를 ProductSummaryInfo 페이지로 변환해 반환한다.")
        @Test
        void returnsSummaryInfoPage_whenRequestIsValid() {
            // arrange
            ProductSummary summary = new ProductSummary(1L, "감성 가디건", 1L, "감성 브랜드", 39_000, 5, 3);
            given(productRepository.findActiveSummaries(null, ProductSortType.LATEST, 0, 20))
                .willReturn(new PageImpl<>(List.of(summary)));

            // act
            Page<ProductSummaryInfo> result = productFacade.readProducts(null, ProductSortType.LATEST, 0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).isAvailable()).isTrue(),
                () -> assertThat(result.getContent().get(0).likeCount()).isEqualTo(3),
                () -> then(productRepository).should().findActiveSummaries(null, ProductSortType.LATEST, 0, 20)
            );
        }

        @DisplayName("재고가 0이면 가용 여부가 false로 변환된다.")
        @Test
        void marksUnavailable_whenStockIsZero() {
            // arrange
            ProductSummary summary = new ProductSummary(1L, "감성 가디건", 1L, "감성 브랜드", 39_000, 0, 0);
            given(productRepository.findActiveSummaries(null, ProductSortType.LATEST, 0, 20))
                .willReturn(new PageImpl<>(List.of(summary)));

            // act
            Page<ProductSummaryInfo> result = productFacade.readProducts(null, ProductSortType.LATEST, 0, 20);

            // assert
            assertThat(result.getContent().get(0).isAvailable()).isFalse();
        }
    }

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class ReadProduct {

        @DisplayName("활성 상품이면 상세 정보를 변환해 반환한다.")
        @Test
        void returnsDetailInfo_whenProductIsActive() {
            // arrange
            ProductDetail detail = new ProductDetail(1L, "감성 가디건", "포근한 가디건", 1L, "감성 브랜드", 39_000, 5, 2);
            given(productRepository.getActiveDetailById(1L)).willReturn(detail);

            // act
            ProductDetailInfo result = productFacade.readProduct(1L);

            // assert
            assertAll(
                () -> assertThat(result.productId()).isEqualTo(1L),
                () -> assertThat(result.isAvailable()).isTrue(),
                () -> assertThat(result.likeCount()).isEqualTo(2)
            );
        }

        @DisplayName("상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(productRepository.getActiveDetailById(1L))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.readProduct(1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("관리자 상품 목록을 조회할 때,")
    @Nested
    class ReadProductsForAdmin {

        @DisplayName("정상 요청이면 조회 결과를 ProductAdminInfo 페이지로 변환해 반환한다.")
        @Test
        void returnsAdminInfoPage_whenRequestIsValid() {
            // arrange
            ProductAdminView view =
                new ProductAdminView(1L, "감성 가디건", "설명", 1L, "감성 브랜드", 39_000, 50, ZonedDateTime.now(), ZonedDateTime.now());
            given(productRepository.findActiveAdminViews(null, 0, 20)).willReturn(new PageImpl<>(List.of(view)));

            // act
            Page<ProductAdminInfo> result = productFacade.readProductsForAdmin(null, 0, 20);

            // assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(1),
                () -> assertThat(result.getContent().get(0).stock()).isEqualTo(50),
                () -> then(productRepository).should().findActiveAdminViews(null, 0, 20)
            );
        }
    }

    @DisplayName("관리자 상품 상세를 조회할 때,")
    @Nested
    class ReadProductForAdmin {

        @DisplayName("활성 상품이면 상세 정보를 변환해 반환한다.")
        @Test
        void returnsAdminInfo_whenProductIsActive() {
            // arrange
            ProductAdminView view =
                new ProductAdminView(1L, "감성 가디건", "설명", 1L, "감성 브랜드", 39_000, 50, ZonedDateTime.now(), ZonedDateTime.now());
            given(productRepository.getActiveAdminViewById(1L)).willReturn(view);

            // act
            ProductAdminInfo result = productFacade.readProductForAdmin(1L);

            // assert
            assertAll(
                () -> assertThat(result.productId()).isEqualTo(1L),
                () -> assertThat(result.stock()).isEqualTo(50)
            );
        }

        @DisplayName("상품이 없거나 삭제되어 조회에 실패하면 NOT_FOUND 예외가 전파된다.")
        @Test
        void throwsNotFound_whenProductIsAbsent() {
            // arrange
            given(productRepository.getActiveAdminViewById(1L))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.readProductForAdmin(1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

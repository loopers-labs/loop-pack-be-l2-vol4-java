package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.SortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private ProductDomainService productDomainService;

    private BrandModel brand;
    private ProductModel product;
    private StockModel stock;

    @BeforeEach
    void setUp() {
        brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
        stock = new StockModel(product, 100);
    }

    @DisplayName("create()를 호출할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 브랜드와 상품 정보로 생성 시 Product와 Stock이 저장되고 ProductInfo가 반환된다.")
        @Test
        void returnsProductInfo_whenValidCommandProvided() {
            // arrange
            ProductCreateCommand command = new ProductCreateCommand(1L, "나이키 에어맥스", 150_000, 100);
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            given(productRepository.save(any(ProductModel.class))).willReturn(product);
            given(stockRepository.save(any(StockModel.class))).willReturn(stock);

            // act
            ProductInfo result = productService.create(command);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(result.price()).isEqualTo(150_000),
                () -> assertThat(result.brandName()).isEqualTo("Nike"),
                () -> assertThat(result.stockQuantity()).isEqualTo(100),
                () -> assertThat(result.likeCount()).isEqualTo(0L)
            );
            then(productRepository).should().save(any(ProductModel.class));
            then(stockRepository).should().save(any(StockModel.class));
        }

        @DisplayName("존재하지 않는 브랜드 ID로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandNotFound() {
            // arrange
            given(brandRepository.findById(99L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.create(new ProductCreateCommand(99L, "상품명", 10_000, 10))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(productRepository).should(never()).save(any());
        }

        @DisplayName("삭제된 브랜드로 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIsDeleted() {
            // arrange
            brand.delete();
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "삭제된 브랜드입니다."))
                .given(productDomainService).validateBrand(brand); // Domain Service 위임 검증

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.create(new ProductCreateCommand(1L, "상품명", 10_000, 10))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(productRepository).should(never()).save(any());
        }
    }

    @DisplayName("getById()를 호출할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 활성 상품 조회 시 ProductInfo가 반환된다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // arrange
            given(productRepository.findActiveById(1L)).willReturn(Optional.of(product));
            given(stockRepository.findByProductId(1L)).willReturn(Optional.of(stock));

            // act
            ProductInfo result = productService.getById(1L);

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(result.brandName()).isEqualTo("Nike"),
                () -> assertThat(result.stockQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("존재하지 않는 ID 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            given(productRepository.findActiveById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.getById(999L)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAll()를 호출할 때,")
    @Nested
    class GetAll {

        @DisplayName("활성 상품 목록을 페이지로 반환한다.")
        @Test
        void returnsPageOfProductInfo_whenProductsExist() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            ProductSearchCondition condition = ProductSearchCondition.of(null, SortType.LATEST);
            Page<ProductModel> page = new PageImpl<>(List.of(product), pageable, 1);
            given(productRepository.findAllActive(pageable, condition)).willReturn(page);
            given(stockRepository.findByProductId(product.getId())).willReturn(Optional.of(stock));

            // act
            Page<ProductInfo> result = productService.getAll(pageable, condition);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("나이키 에어맥스");
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시 수정된 ProductInfo가 반환된다.")
        @Test
        void returnsUpdatedProductInfo_whenValidCommandProvided() {
            // arrange
            given(productRepository.findActiveById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(product)).willReturn(product);
            given(stockRepository.findByProductId(1L)).willReturn(Optional.of(stock));

            // act
            ProductInfo result = productService.update(1L, new ProductUpdateCommand("뉴발란스 990", 200_000));

            // assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("뉴발란스 990"),
                () -> assertThat(result.price()).isEqualTo(200_000),
                () -> assertThat(result.brandName()).isEqualTo("Nike") // 브랜드 불변
            );
        }

        @DisplayName("존재하지 않는 ID 수정 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            given(productRepository.findActiveById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.update(999L, new ProductUpdateCommand("상품명", 10_000))
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(productRepository).should(never()).save(any());
        }
    }

    @DisplayName("delete()를 호출할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품 삭제 시 소프트딜리트 후 저장된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            // arrange
            given(productRepository.findActiveById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(product)).willReturn(product);

            // act
            productService.delete(1L);

            // assert
            assertThat(product.isDeleted()).isTrue();
            then(productRepository).should().save(product);
        }

        @DisplayName("존재하지 않는 ID 삭제 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            given(productRepository.findActiveById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productService.delete(999L)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("deleteAllByBrandId()를 호출할 때,")
    @Nested
    class DeleteAllByBrandId {

        @DisplayName("해당 브랜드의 활성 상품이 모두 소프트딜리트된다.")
        @Test
        void softDeletesAllProducts_whenBrandHasActiveProducts() {
            // arrange
            ProductModel product2 = new ProductModel(brand, "나이키 조던", 200_000);
            given(productRepository.findAllByBrandId(1L)).willReturn(List.of(product, product2));
            given(productRepository.save(any(ProductModel.class))).willReturn(product);

            // act
            productService.deleteAllByBrandId(1L);

            // assert
            assertThat(product.isDeleted()).isTrue();
            assertThat(product2.isDeleted()).isTrue();
        }

        @DisplayName("이미 삭제된 상품은 중복 삭제되지 않는다.")
        @Test
        void skipsAlreadyDeletedProducts() {
            // arrange
            product.delete();
            given(productRepository.findAllByBrandId(1L)).willReturn(List.of(product));

            // act
            productService.deleteAllByBrandId(1L);

            // assert: 이미 삭제된 상품이라 save가 호출되지 않아야 함
            then(productRepository).should(never()).save(any());
        }
    }
}

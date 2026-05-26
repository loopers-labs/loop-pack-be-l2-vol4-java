package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class BrandFacadeTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private BrandFacade brandFacade;

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class CreateBrand {

        private final String name = "감성 브랜드";
        private final String description = "감성을 담은 브랜드";

        @DisplayName("활성 이름이 중복되지 않으면 브랜드를 저장하고 생성 정보를 반환한다.")
        @Test
        void returnsCreateInfo_whenNameIsAvailable() {
            // arrange
            given(brandRepository.existsActiveByName(name)).willReturn(false);
            given(brandRepository.save(any(BrandModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            BrandCreateInfo createInfo = brandFacade.createBrand(name, description);

            // assert
            assertAll(
                () -> assertThat(createInfo).isNotNull(),
                () -> then(brandRepository).should().existsActiveByName(name),
                () -> then(brandRepository).should().save(any(BrandModel.class))
            );
        }

        @DisplayName("활성 이름이 이미 존재하면 CONFLICT 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsConflict_whenActiveNameAlreadyExists() {
            // arrange
            given(brandRepository.existsActiveByName(name)).willReturn(true);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> brandFacade.createBrand(name, description))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> then(brandRepository).should(never()).save(any(BrandModel.class))
            );
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        private final Long brandId = 1L;

        @DisplayName("대상이 활성 존재하고 새 이름이 다른 활성과 중복되지 않으면 갱신하고 정보를 반환한다.")
        @Test
        void updatesBrand_whenTargetActiveAndNameAvailable() {
            // arrange
            BrandModel brand = BrandModel.builder()
                .rawName("기존 브랜드")
                .rawDescription("기존 설명")
                .build();
            given(brandRepository.getActiveById(brandId)).willReturn(brand);
            given(brandRepository.existsActiveByNameAndIdNot("새 브랜드", brandId)).willReturn(false);

            // act
            BrandUpdateInfo updateInfo = brandFacade.updateBrand(brandId, "새 브랜드", "새 설명");

            // assert
            assertAll(
                () -> assertThat(updateInfo).isNotNull(),
                () -> assertThat(brand.getName().value()).isEqualTo("새 브랜드"),
                () -> assertThat(brand.getDescription()).isEqualTo("새 설명")
            );
        }

        @DisplayName("대상 브랜드가 활성 상태로 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTargetIsAbsent() {
            // arrange
            given(brandRepository.getActiveById(brandId)).willThrow(new CoreException(ErrorType.NOT_FOUND));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> brandFacade.updateBrand(brandId, "새 브랜드", "새 설명"))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> then(brandRepository).should(never()).existsActiveByNameAndIdNot(any(), any())
            );
        }

        @DisplayName("새 이름이 다른 활성 브랜드와 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewNameDuplicatesOtherActive() {
            // arrange
            BrandModel brand = BrandModel.builder()
                .rawName("기존 브랜드")
                .rawDescription("기존 설명")
                .build();
            given(brandRepository.getActiveById(brandId)).willReturn(brand);
            given(brandRepository.existsActiveByNameAndIdNot("중복 브랜드", brandId)).willReturn(true);

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> brandFacade.updateBrand(brandId, "중복 브랜드", "새 설명"))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(brand.getName().value()).isEqualTo("기존 브랜드")
            );
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        private final Long brandId = 1L;

        @DisplayName("대상 브랜드가 활성 상태로 존재하면 브랜드와 소속 활성 상품이 모두 삭제된다.")
        @Test
        void deletesBrand_andCascadesProducts_whenBrandIsActive() {
            // arrange
            BrandModel brand = BrandModel.builder()
                .rawName("감성 브랜드")
                .rawDescription("감성 설명")
                .build();
            ProductModel product1 = ProductModel.builder()
                .brandId(brandId)
                .rawName("상품1")
                .rawDescription("설명")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            ProductModel product2 = ProductModel.builder()
                .brandId(brandId)
                .rawName("상품2")
                .rawDescription("설명")
                .rawPrice(39_000)
                .rawStock(50)
                .build();
            given(brandRepository.findActiveById(brandId)).willReturn(Optional.of(brand));
            given(productRepository.findActiveByBrandId(brandId)).willReturn(List.of(product1, product2));

            // act
            brandFacade.deleteBrand(brandId);

            // assert
            assertAll(
                () -> assertThat(brand.getDeletedAt()).isNotNull(),
                () -> assertThat(product1.getDeletedAt()).isNotNull(),
                () -> assertThat(product2.getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("대상 브랜드가 없거나 이미 삭제되었으면 상품 조회·삭제도 하지 않는다(멱등).")
        @Test
        void doesNothing_whenBrandIsAbsent() {
            // arrange
            given(brandRepository.findActiveById(brandId)).willReturn(Optional.empty());

            // act
            brandFacade.deleteBrand(brandId);

            // assert
            then(productRepository).should(never()).findActiveByBrandId(any());
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class ReadBrand {

        private final Long brandId = 1L;

        @DisplayName("활성 브랜드가 존재하면 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenActiveExists() {
            // arrange
            BrandModel brand = BrandModel.builder()
                .rawName("감성 브랜드")
                .rawDescription("감성 설명")
                .build();
            given(brandRepository.getActiveById(brandId)).willReturn(brand);

            // act
            BrandInfo brandInfo = brandFacade.readBrand(brandId);

            // assert
            assertAll(
                () -> assertThat(brandInfo.name()).isEqualTo("감성 브랜드"),
                () -> assertThat(brandInfo.description()).isEqualTo("감성 설명")
            );
        }

        @DisplayName("활성 브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenActiveAbsent() {
            // arrange
            given(brandRepository.getActiveById(brandId)).willThrow(new CoreException(ErrorType.NOT_FOUND));

            // act & assert
            assertThatThrownBy(() -> brandFacade.readBrand(brandId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class ReadBrands {

        @DisplayName("활성 브랜드 페이지를 BrandInfo로 매핑해 반환한다.")
        @Test
        void returnsMappedPage() {
            // arrange
            BrandModel brand = BrandModel.builder()
                .rawName("감성 브랜드")
                .rawDescription("감성 설명")
                .build();
            given(brandRepository.findActiveByPage(0, 20))
                .willReturn(new PageImpl<>(List.of(brand)));

            // act
            Page<BrandInfo> brandsInfo = brandFacade.readBrands(0, 20);

            // assert
            assertAll(
                () -> assertThat(brandsInfo.getTotalElements()).isEqualTo(1),
                () -> assertThat(brandsInfo.getContent()).extracting(BrandInfo::name).containsExactly("감성 브랜드"),
                () -> then(brandRepository).should().findActiveByPage(0, 20)
            );
        }
    }
}

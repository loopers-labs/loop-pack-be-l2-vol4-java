package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandServiceTest {

    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandService brandService =
            new BrandService(brandRepository, brandReader, productRepository, productStockRepository);

    @Test
    @DisplayName("create 커맨드로 브랜드를 저장한다")
    void givenCreateCommand_whenCreate_thenSavesBrand() {
        BrandCommand.Create command = new BrandCommand.Create("루퍼스", "트렌디한 라이프스타일");
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        brandService.create(command);

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(captor.capture());
        Brand saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getName()).isEqualTo("루퍼스"),
                () -> assertThat(saved.getDescription()).isEqualTo("트렌디한 라이프스타일")
        );
    }

    @Test
    @DisplayName("update 커맨드로 기존 브랜드의 이름과 설명이 변경된다")
    void givenUpdateCommand_whenUpdate_thenChangesBrandFields() {
        Brand brand = Brand.create("원래이름", "원래설명");
        when(brandReader.get(1L)).thenReturn(brand);
        BrandCommand.Update command = new BrandCommand.Update(1L, "새이름", "새설명");

        brandService.update(command);

        assertAll(
                () -> assertThat(brand.getName()).isEqualTo("새이름"),
                () -> assertThat(brand.getDescription()).isEqualTo("새설명")
        );
    }

    @Test
    @DisplayName("update 시 존재하지 않는 brandId 이면 reader 가 던진 NOT_FOUND 예외가 전파된다")
    void givenNonExistingId_whenUpdate_thenPropagatesNotFound() {
        when(brandReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        BrandCommand.Update command = new BrandCommand.Update(999L, "새이름", "새설명");

        assertThatThrownBy(() -> brandService.update(command))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("delete 호출 시 stock, product, brand 순으로 bulk soft delete 가 수행된다")
    void givenExistingBrandId_whenDelete_thenBulkSoftDeletesStockProductBrand() {
        when(brandReader.get(1L)).thenReturn(Brand.create("루퍼스", "설명"));

        brandService.delete(1L);

        verify(productStockRepository).softDeleteByBrandId(1L);
        verify(productRepository).softDeleteByBrandId(1L);
        verify(brandRepository).softDeleteById(1L);
    }

    @Test
    @DisplayName("delete 시 존재하지 않는 brandId 이면 reader 가 던진 NOT_FOUND 가 전파되고 cascade 는 일어나지 않는다")
    void givenNonExistingId_whenDelete_thenPropagatesNotFoundAndDoesNotCascade() {
        when(brandReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        assertThatThrownBy(() -> brandService.delete(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(productStockRepository, org.mockito.Mockito.never()).softDeleteByBrandId(any());
        verify(productRepository, org.mockito.Mockito.never()).softDeleteByBrandId(any());
        verify(brandRepository, org.mockito.Mockito.never()).softDeleteById(any());
    }

    @Test
    @DisplayName("get 은 reader 가 반환한 brand 를 Detail 로 매핑해서 반환한다")
    void givenExistingBrandId_whenGet_thenReturnsBrandDetail() {
        Brand brand = Brand.create("루퍼스", "설명");
        when(brandReader.get(1L)).thenReturn(brand);

        BrandResult.Detail result = brandService.get(1L);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("루퍼스"),
                () -> assertThat(result.description()).isEqualTo("설명")
        );
    }

    @Test
    @DisplayName("getAll 은 repository 의 brand 들을 Detail 리스트로 매핑한다")
    void givenBrands_whenGetAll_thenReturnsBrandDetails() {
        Brand a = Brand.create("A", "설명A");
        Brand b = Brand.create("B", "설명B");
        when(brandRepository.findAll()).thenReturn(List.of(a, b));

        List<BrandResult.Detail> result = brandService.getAll();

        assertThat(result)
                .extracting(BrandResult.Detail::name)
                .containsExactly("A", "B");
    }
}

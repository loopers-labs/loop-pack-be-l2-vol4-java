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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandAdminServiceTest {

    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandAdminService brandAdminService =
            new BrandAdminService(brandRepository, brandReader, productRepository, productStockRepository);

    @Test
    @DisplayName("create 커맨드로 브랜드를 저장한다")
    void givenCreateCommand_whenCreate_thenSavesBrand() {
        BrandCommand.Create command =
                new BrandCommand.Create("루퍼스", "트렌디한 라이프스타일", "https://cdn.loopers.com/l.png");
        when(brandRepository.existsByName("루퍼스")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> inv.getArgument(0));

        brandAdminService.create(command);

        ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(captor.capture());
        Brand saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getName()).isEqualTo("루퍼스"),
                () -> assertThat(saved.getDescription()).isEqualTo("트렌디한 라이프스타일"),
                () -> assertThat(saved.getLogoUrl()).isEqualTo("https://cdn.loopers.com/l.png")
        );
    }

    @Test
    @DisplayName("create 시 이미 존재하는 브랜드명이면 CONFLICT 가 발생하고 저장하지 않는다")
    void givenDuplicateName_whenCreate_thenThrowsConflict() {
        when(brandRepository.existsByName("루퍼스")).thenReturn(true);
        BrandCommand.Create command = new BrandCommand.Create("루퍼스", "설명", null);

        assertThatThrownBy(() -> brandAdminService.create(command))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);

        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("update 커맨드로 기존 브랜드의 필드가 변경된다")
    void givenUpdateCommand_whenUpdate_thenChangesBrandFields() {
        Brand brand = Brand.create("원래이름", "원래설명", null);
        when(brandReader.get(1L)).thenReturn(brand);
        BrandCommand.Update command =
                new BrandCommand.Update(1L, "새이름", "새설명", "https://cdn.loopers.com/new.png");

        brandAdminService.update(command);

        assertAll(
                () -> assertThat(brand.getName()).isEqualTo("새이름"),
                () -> assertThat(brand.getDescription()).isEqualTo("새설명"),
                () -> assertThat(brand.getLogoUrl()).isEqualTo("https://cdn.loopers.com/new.png")
        );
    }

    @Test
    @DisplayName("update 시 다른 브랜드가 쓰는 이름으로 바꾸면 CONFLICT 가 발생한다")
    void givenNameUsedByAnother_whenUpdate_thenThrowsConflict() {
        Brand brand = Brand.create("원래이름", "설명", null);
        when(brandReader.get(1L)).thenReturn(brand);
        when(brandRepository.existsByName("중복이름")).thenReturn(true);
        BrandCommand.Update command = new BrandCommand.Update(1L, "중복이름", "설명", null);

        assertThatThrownBy(() -> brandAdminService.update(command))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("update 시 이름을 그대로 두면 중복 검사를 건너뛴다")
    void givenSameName_whenUpdate_thenSkipsDuplicateCheck() {
        Brand brand = Brand.create("루퍼스", "설명", null);
        when(brandReader.get(1L)).thenReturn(brand);
        BrandCommand.Update command = new BrandCommand.Update(1L, "루퍼스", "새설명", null);

        brandAdminService.update(command);

        assertThat(brand.getDescription()).isEqualTo("새설명");
        verify(brandRepository, never()).existsByName(anyString());
    }

    @Test
    @DisplayName("update 시 존재하지 않는 brandId 이면 reader 가 던진 NOT_FOUND 예외가 전파된다")
    void givenNonExistingId_whenUpdate_thenPropagatesNotFound() {
        when(brandReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        BrandCommand.Update command = new BrandCommand.Update(999L, "새이름", "새설명", null);

        assertThatThrownBy(() -> brandAdminService.update(command))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("delete 호출 시 stock, product, brand 순으로 bulk soft delete 가 수행된다")
    void givenExistingBrandId_whenDelete_thenBulkSoftDeletesStockProductBrand() {
        when(brandReader.get(1L)).thenReturn(Brand.create("루퍼스", "설명", null));

        brandAdminService.delete(1L);

        verify(productStockRepository).softDeleteByBrandId(1L);
        verify(productRepository).softDeleteByBrandId(1L);
        verify(brandRepository).softDeleteById(1L);
    }

    @Test
    @DisplayName("delete 시 존재하지 않는 brandId 이면 NOT_FOUND 가 전파되고 cascade 는 일어나지 않는다")
    void givenNonExistingId_whenDelete_thenPropagatesNotFoundAndDoesNotCascade() {
        when(brandReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        assertThatThrownBy(() -> brandAdminService.delete(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(productStockRepository, never()).softDeleteByBrandId(any());
        verify(productRepository, never()).softDeleteByBrandId(any());
        verify(brandRepository, never()).softDeleteById(any());
    }
}

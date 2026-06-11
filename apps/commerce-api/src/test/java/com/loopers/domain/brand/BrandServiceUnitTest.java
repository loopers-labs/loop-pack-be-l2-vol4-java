package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceUnitTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @DisplayName("브랜드 단건 조회할 때")
    @Nested
    class GetBrand {

        @DisplayName("존재한다면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenExists() {
            // given
            BrandModel brand = BrandModel.of(BrandName.of("나이키"), BrandDescription.of("설명"));
            given(brandRepository.find(1L)).willReturn(Optional.of(brand));

            // when
            BrandModel result = brandService.getBrand(1L);

            // then
            assertThat(result.getName().value()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않거나 삭제되었다면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // given
            given(brandRepository.find(1L)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> brandService.getBrand(1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("목록·검색 조회할 때")
    @Nested
    class GetBrands {

        @DisplayName("keyword가 null이면, 전체 목록을 호출한다.")
        @Test
        void callsFindAll_whenQueryIsNull() {
            // given
            Page<BrandModel> page = new PageImpl<>(List.of(
                    BrandModel.of(BrandName.of("나이키"), BrandDescription.of("설명"))
            ));
            given(brandRepository.findAllNotDeleted(any(Pageable.class))).willReturn(page);

            // when
            Page<BrandModel> result = brandService.getBrands(null, 0, 10);

            // then
            verify(brandRepository).findAllNotDeleted(any(Pageable.class));
            verify(brandRepository, never()).findByNameContainingAndNotDeleted(any(), any());
            assertThat(result.getContent()).hasSize(1);
        }

        @DisplayName("keyword가 비어있더라도 null이 아니면, keyword 조회를 호출한다.")
        @Test
        void callsFindByName_whenQueryNotNull() {
            // given
            Page<BrandModel> page = new PageImpl<>(List.of());
            given(brandRepository.findByNameContainingAndNotDeleted(eq(""), any(Pageable.class)))
                    .willReturn(page);

            // when
            brandService.getBrands("", 0, 10);

            // then
            verify(brandRepository).findByNameContainingAndNotDeleted(eq(""), any(Pageable.class));
            verify(brandRepository, never()).findAllNotDeleted(any());
        }
    }
}

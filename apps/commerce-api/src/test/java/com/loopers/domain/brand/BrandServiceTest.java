package com.loopers.domain.brand;

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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @DisplayName("ID로 조회 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드 ID로 조회하면 브랜드를 반환한다")
        @Test
        void returnsBrand_whenIdExists() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("Loopers", "감성 라이프스타일 브랜드");
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            // when
            BrandModel result = brandService.getById(brandId);

            // then
            assertThat(result).isSameAs(brand);
        }

        @DisplayName("존재하지 않는 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // given
            Long brandId = 999L;
            when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> brandService.getById(brandId));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("목록 조회 시")
    @Nested
    class Search {

        @DisplayName("페이지 요청에 대해 Repository가 반환한 Page를 그대로 반환한다")
        @Test
        void returnsPage_fromRepository() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            BrandModel brand = new BrandModel("Loopers", "감성 라이프스타일 브랜드");
            Page<BrandModel> page = new PageImpl<>(List.of(brand), pageable, 1);
            when(brandRepository.search(pageable)).thenReturn(page);

            // when
            Page<BrandModel> result = brandService.search(pageable);

            // then
            assertThat(result.getContent()).containsExactly(brand);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}

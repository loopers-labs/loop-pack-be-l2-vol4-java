package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandServiceTest {

    private BrandService brandService;
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드면 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // given
            Long id = 1L;
            BrandModel brand = new BrandModel("Nike");
            when(brandRepository.findById(id)).thenReturn(Optional.of(brand));

            // when
            BrandModel result = brandService.getById(id);

            // then
            assertAll(
                    () -> assertThat(result).isSameAs(brand),
                    () -> assertThat(result.getName()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // given
            Long id = 999L;
            when(brandRepository.findById(id)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> brandService.getById(id));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("여러 ID로 브랜드 맵을 조회할 때, ")
    @Nested
    class GetMapByIds {

        @DisplayName("존재하는 브랜드의 ID → BrandModel 맵을 반환한다.")
        @Test
        void returnsBrandMap_whenBrandExists() {
            // given
            Set<Long> ids = Set.of(1L);
            BrandModel nike = new BrandModel("Nike");
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of(nike));

            // when
            Map<Long, BrandModel> result = brandService.getMapByIds(ids);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result).containsValue(nike)
            );
        }

        @DisplayName("일부 ID에 해당하는 브랜드가 없으면 존재하는 것만 맵에 담아 반환한다.")
        @Test
        void returnsPartialBrandMap_whenSomeIdsDoNotExist() {
            // given
            Set<Long> ids = Set.of(1L, 999L);
            BrandModel nike = new BrandModel("Nike");
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of(nike));

            // when
            Map<Long, BrandModel> result = brandService.getMapByIds(ids);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result).containsValue(nike)
            );
        }

        @DisplayName("빈 ID 목록이면 빈 맵을 반환한다.")
        @Test
        void returnsEmptyMap_whenIdsIsEmpty() {
            // given
            Set<Long> ids = Set.of();
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of());

            // when
            Map<Long, BrandModel> result = brandService.getMapByIds(ids);

            // then
            assertThat(result).isEmpty();
        }
    }
}

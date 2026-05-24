package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandFinderTest {

    private BrandFinder brandFinder;
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        brandFinder = new BrandFinder(brandRepository);
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
            BrandModel result = brandFinder.getById(id);

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
            CoreException result = assertThrows(CoreException.class, () -> brandFinder.getById(id));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("여러 ID로 브랜드를 조회할 때, ")
    @Nested
    class GetAllByIds {

        @DisplayName("모든 ID에 해당하는 브랜드가 존재하면 전부 반환한다.")
        @Test
        void returnsAllBrands_whenAllIdsExist() {
            // given
            BrandModel nike = new BrandModel("Nike");
            BrandModel adidas = new BrandModel("Adidas");
            Set<Long> ids = Set.of(1L, 2L);
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of(nike, adidas));

            // when
            List<BrandModel> result = brandFinder.getAllByIds(ids);

            // then
            assertThat(result).containsExactlyInAnyOrder(nike, adidas);
        }

        @DisplayName("일부 ID에 해당하는 브랜드가 없으면 존재하는 것만 반환한다.")
        @Test
        void returnsPartialBrands_whenSomeIdsDoNotExist() {
            // given
            BrandModel nike = new BrandModel("Nike");
            Set<Long> ids = Set.of(1L, 999L);
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of(nike));

            // when
            List<BrandModel> result = brandFinder.getAllByIds(ids);

            // then
            assertThat(result).containsExactly(nike);
        }

        @DisplayName("빈 ID 목록이면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenIdsIsEmpty() {
            // given
            Set<Long> ids = Set.of();
            when(brandRepository.findAllByIdIn(ids)).thenReturn(List.of());

            // when
            List<BrandModel> result = brandFinder.getAllByIds(ids);

            // then
            assertThat(result).isEmpty();
        }
    }
}

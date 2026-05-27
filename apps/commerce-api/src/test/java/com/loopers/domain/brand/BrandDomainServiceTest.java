package com.loopers.domain.brand;

import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandDomainServiceTest {

    private BrandRepository brandRepository;
    private BrandDomainService brandDomainService;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        brandDomainService = new BrandDomainService(brandRepository);
    }

    @DisplayName("브랜드 이름 중복을 검증할 때, ")
    @Nested
    class ValidateDuplicateName {

        @DisplayName("중복되지 않은 이름이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNameIsUnique() {
            // Arrange
            when(brandRepository.existsByName("나이키")).thenReturn(false);

            // Act & Assert
            brandDomainService.validateDuplicateName("나이키");
        }

        @DisplayName("이미 존재하는 이름이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // Arrange
            when(brandRepository.existsByName("나이키")).thenReturn(true);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brandDomainService.validateDuplicateName("나이키")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }


}

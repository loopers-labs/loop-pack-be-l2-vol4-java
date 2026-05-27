package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BrandApplicationServiceTest {

    private BrandDomainService brandDomainService;
    private BrandRepository brandRepository;
    private BrandApplicationService brandApplicationService;

    @BeforeEach
    void setUp() {
        brandDomainService = mock(BrandDomainService.class);
        brandRepository = mock(BrandRepository.class);
        brandApplicationService = new BrandApplicationService(brandDomainService, brandRepository);
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 이름이 주어지면, save()가 호출된다.")
        @Test
        void callsSave_whenNameIsValid() {
            // Arrange
            String name = "나이키";
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            brandApplicationService.register(name);

            // Assert
            verify(brandDomainService, times(1)).validateDuplicateName(name);
            verify(brandRepository, times(1)).save(any(Brand.class));
        }

        @DisplayName("중복된 이름이면, CONFLICT 예외가 발생하고 save()는 호출되지 않는다.")
        @Test
        void throwsConflict_whenNameIsDuplicated() {
            // Arrange
            String name = "나이키";
            doThrow(new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다."))
                .when(brandDomainService).validateDuplicateName(name);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.register(name)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(brandRepository, never()).save(any(Brand.class));
        }
    }
}

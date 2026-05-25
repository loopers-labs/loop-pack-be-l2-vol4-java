package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class BrandFacadeTest {

    @Mock
    private BrandRepository brandRepository;

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
}

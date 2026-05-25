package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class BrandServiceIntegrationTest {

    @Autowired BrandService brandService;
    @Autowired BrandRepository brandRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("브랜드를 등록할 때")
    class Register {

        @DisplayName("유효한 정보로 등록하면, 저장되고 활성 상태로 반환된다.")
        @Test
        void given_validInput_when_register_then_savedAndActive() {
            // Act
            BrandModel result = brandService.register("나이키", "글로벌 스포츠 브랜드");

            // Assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getName()).isEqualTo("나이키"),
                    () -> assertThat(result.getDescription()).isEqualTo("글로벌 스포츠 브랜드"),
                    () -> assertThat(result.isActive()).isTrue()
            );
        }
    }

    @Nested
    @DisplayName("활성 브랜드를 조회할 때")
    class GetActiveBrand {

        @DisplayName("존재하는 활성 브랜드를 조회하면, 브랜드를 반환한다.")
        @Test
        void given_activeBrand_when_getActiveBrand_then_returnsBrand() {
            // Arrange
            BrandModel saved = brandService.register("나이키", "설명");

            // Act
            BrandModel result = brandService.getActiveBrand(saved.getId());

            // Assert
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 id로 조회하면, NotFound 예외가 발생한다.")
        @Test
        void given_nonExistingId_when_getActiveBrand_then_throwsNotFound() {
            // Act
            Throwable thrown = catchThrowable(() -> brandService.getActiveBrand(9999L));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된(비활성) 브랜드를 조회하면, NotFound 예외가 발생한다. (01 §7.4 정보 노출 방지)")
        @Test
        void given_deletedBrand_when_getActiveBrand_then_throwsNotFound() {
            // Arrange
            BrandModel saved = brandService.register("나이키", "설명");
            saved.delete();
            brandRepository.save(saved);

            // Act
            Throwable thrown = catchThrowable(() -> brandService.getActiveBrand(saved.getId()));

            // Assert
            assertThat(thrown).isInstanceOf(CoreException.class);
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

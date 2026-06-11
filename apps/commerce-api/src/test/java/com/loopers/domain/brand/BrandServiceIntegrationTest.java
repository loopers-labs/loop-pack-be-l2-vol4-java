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
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name, String description) {
        return brandRepository.save(BrandModel.of(BrandName.of(name), BrandDescription.of(description)));
    }

    @DisplayName("브랜드 단건 조회할 때")
    @Nested
    class GetBrand {

        @DisplayName("존재한다면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenExists() {
            // given
            BrandModel saved = saveBrand("나이키", "스포츠 브랜드");

            // when
            BrandModel result = brandService.getBrand(saved.getId());

            // then
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName().value()).isEqualTo("나이키");
            assertThat(result.getDescription().value()).isEqualTo("스포츠 브랜드");
        }

        @DisplayName("존재하지 않으면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            CoreException result = assertThrows(CoreException.class,
                    () -> brandService.getBrand(99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenSoftDeleted() {
            // given
            BrandModel saved = saveBrand("나이키", "스포츠 브랜드");
            saved.delete();
            brandRepository.save(saved);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> brandService.getBrand(saved.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("목록·검색 조회할 때")
    @Nested
    class GetBrands {

        @DisplayName("keyword가 null이면, 전체 브랜드를 이름 오름차순으로 반환한다.")
        @Test
        void returnsAllNotDeleted_orderedByName() {
            // given
            saveBrand("나이키", "스포츠 브랜드");
            saveBrand("아디다스", "독일 스포츠 브랜드");
            saveBrand("가나다", "기타 브랜드");
            BrandModel deleted = saveBrand("삭제됨", "삭제될 브랜드");
            deleted.delete();
            brandRepository.save(deleted);

            // when
            Page<BrandModel> result = brandService.getBrands(null, 0, 10);

            // then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent()).extracting(b -> b.getName().value())
                    .containsExactly("가나다", "나이키", "아디다스");
        }

        @DisplayName("keyword가 있으면, 이름에 부분 일치하는 브랜드만 반환한다.")
        @Test
        void returnsFilteredByName_whenQueryProvided() {
            // given
            saveBrand("나이키", "스포츠 브랜드");
            saveBrand("나이스원", "기타 브랜드");
            saveBrand("아디다스", "독일 스포츠 브랜드");

            // when
            Page<BrandModel> result = brandService.getBrands("나이", 0, 10);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).extracting(b -> b.getName().value())
                    .containsExactly("나이스원", "나이키");
        }

        @DisplayName("일치하는 브랜드가 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmpty_whenNoMatch() {
            // given
            saveBrand("나이키", "스포츠 브랜드");

            // when
            Page<BrandModel> result = brandService.getBrands("zzz", 0, 10);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}

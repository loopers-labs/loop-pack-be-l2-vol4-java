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
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BrandService 통합 — H2 인메모리에서 실제 JPA 쿼리·soft delete 필터·페이징 동작을 검증한다.
 * 단위 테스트(BrandServiceTest)는 분기/예외, 통합은 영속/쿼리 동작을 본다.
 */
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

    @DisplayName("단건 조회 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드면 그대로 반환한다")
        @Test
        void returnsBrand_whenExists() {
            // given
            BrandModel saved = brandRepository.save(new BrandModel("Loopers", "감성"));

            // when
            BrandModel found = brandService.getById(saved.getId());

            // then
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getName()).isEqualTo("Loopers")
            );
        }

        @DisplayName("soft delete된 브랜드는 NOT_FOUND가 발생한다 (deletedAt IS NULL 필터)")
        @Test
        void throwsNotFound_whenSoftDeleted() {
            // given
            BrandModel saved = brandRepository.save(new BrandModel("Loopers", "감성"));
            saved.delete();
            brandRepository.save(saved);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> brandService.getById(saved.getId()));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("ID 다건 조회 시")
    @Nested
    class FindAllByIds {

        @DisplayName("요청한 ID 중 존재하는 것만 반환한다 (IN 쿼리)")
        @Test
        void returnsOnlyExisting_whenSomeMissing() {
            // given
            BrandModel a = brandRepository.save(new BrandModel("BrandA", "설명A"));
            BrandModel b = brandRepository.save(new BrandModel("BrandB", "설명B"));

            // when
            List<BrandModel> found = brandService.findAllByIds(List.of(a.getId(), b.getId(), 99_999L));

            // then
            assertThat(found).extracting(BrandModel::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
        }

        @DisplayName("soft delete된 브랜드는 결과에서 제외된다")
        @Test
        void excludesSoftDeleted() {
            // given
            BrandModel alive = brandRepository.save(new BrandModel("Alive", "살아있음"));
            BrandModel deleted = brandRepository.save(new BrandModel("Deleted", "삭제됨"));
            deleted.delete();
            brandRepository.save(deleted);

            // when
            List<BrandModel> found = brandService.findAllByIds(List.of(alive.getId(), deleted.getId()));

            // then
            assertThat(found).extracting(BrandModel::getId).containsExactly(alive.getId());
        }

        @DisplayName("빈 컬렉션을 전달하면 빈 List를 반환한다 (early return)")
        @Test
        void returnsEmptyList_whenInputEmpty() {
            // when
            List<BrandModel> found = brandService.findAllByIds(List.of());

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("브랜드 목록 조회 시")
    @Nested
    class Search {

        @DisplayName("페이지 크기에 맞춰 페이징되며 soft delete된 브랜드는 제외된다")
        @Test
        void paginatesAndExcludesSoftDeleted() {
            // given - 살아있는 브랜드 3개 + 삭제된 1개
            brandRepository.save(new BrandModel("B1", "설명1"));
            brandRepository.save(new BrandModel("B2", "설명2"));
            brandRepository.save(new BrandModel("B3", "설명3"));
            BrandModel deleted = brandRepository.save(new BrandModel("Deleted", "삭제됨"));
            deleted.delete();
            brandRepository.save(deleted);

            // when
            Page<BrandModel> page = brandService.search(PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getContent()).extracting(BrandModel::getName).doesNotContain("Deleted")
            );
        }
    }
}

package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceTest {

    private ProductService productService;
    private FakeProductRepository fakeProductRepository;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        productService = new ProductService(fakeProductRepository);
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 상품을 등록하면, 저장되어 반환된다.")
        @Test
        void savesProduct_whenProductIsValid() {
            // arrange
            ProductModel product = new ProductModel("에어포스1", 139000L, 1L);

            // act
            ProductModel saved = productService.create(product);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("에어포스1");
            assertThat(saved.getPrice()).isEqualTo(139000L);
            assertThat(saved.getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품을 조회하면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            ProductModel saved = productService.create(new ProductModel("에어포스1", 139000L, 1L));

            // act
            ProductModel result = productService.getById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo("에어포스1");
        }

        @DisplayName("존재하지 않는 상품을 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> productService.getById(nonExistentId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("기본 정렬(latest)로 조회하면, 최신 등록 순으로 반환된다.")
        @Test
        void returnsProducts_orderedByLatest() {
            // arrange
            productService.create(new ProductModel("상품A", 10000L, 1L));
            productService.create(new ProductModel("상품B", 20000L, 1L));
            productService.create(new ProductModel("상품C", 30000L, 1L));

            // act
            Page<ProductModel> result = productService.getAll(null, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("상품C");
            assertThat(result.getContent().get(2).getName()).isEqualTo("상품A");
        }

        @DisplayName("brandId 필터를 적용하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsProducts_filteredByBrandId() {
            // arrange
            productService.create(new ProductModel("나이키상품", 139000L, 1L));
            productService.create(new ProductModel("아디다스상품", 99000L, 2L));

            // act
            Page<ProductModel> result = productService.getAll(1L, ProductSort.LATEST, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키상품");
        }

        @DisplayName("price_asc 정렬로 조회하면, 가격 오름차순으로 반환된다.")
        @Test
        void returnsProducts_orderedByPriceAsc() {
            // arrange
            productService.create(new ProductModel("상품A", 30000L, 1L));
            productService.create(new ProductModel("상품B", 10000L, 1L));
            productService.create(new ProductModel("상품C", 20000L, 1L));

            // act
            Page<ProductModel> result = productService.getAll(null, ProductSort.PRICE_ASC, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent().get(0).getPrice()).isEqualTo(10000L);
            assertThat(result.getContent().get(1).getPrice()).isEqualTo(20000L);
            assertThat(result.getContent().get(2).getPrice()).isEqualTo(30000L);
        }

        @DisplayName("likes_desc 정렬로 조회하면, likeCount 내림차순으로 반환된다.")
        @Test
        void returnsProducts_orderedByLikesDesc() {
            // arrange
            ProductModel a = productService.create(new ProductModel("상품A", 10000L, 1L));
            ProductModel b = productService.create(new ProductModel("상품B", 10000L, 1L));
            ProductModel c = productService.create(new ProductModel("상품C", 10000L, 1L));
            a.increaseLikeCount();
            a.increaseLikeCount();
            a.increaseLikeCount();
            b.increaseLikeCount();

            // act
            Page<ProductModel> result = productService.getAll(null, ProductSort.LIKES_DESC, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent().get(0).getName()).isEqualTo("상품A");
            assertThat(result.getContent().get(1).getName()).isEqualTo("상품B");
            assertThat(result.getContent().get(2).getName()).isEqualTo("상품C");
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("존재하는 상품을 수정하면, 이름과 가격이 변경된다.")
        @Test
        void updatesProduct_whenProductExists() {
            // arrange
            ProductModel saved = productService.create(new ProductModel("에어포스1", 139000L, 1L));

            // act
            ProductModel updated = productService.update(saved.getId(), "에어맥스90", 159000L);

            // assert
            assertThat(updated.getName()).isEqualTo("에어맥스90");
            assertThat(updated.getPrice()).isEqualTo(159000L);
            assertThat(updated.getBrandId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품을 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> productService.update(nonExistentId, "에어맥스90", 159000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품을 삭제하면, deletedAt이 설정된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            // arrange
            ProductModel saved = productService.create(new ProductModel("에어포스1", 139000L, 1L));

            // act
            productService.delete(saved.getId());

            // assert
            ProductModel deleted = fakeProductRepository.findByIdIgnoringFilter(saved.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 상품을 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> productService.delete(nonExistentId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // ───────────────────────────────────────────────
    // Fake: DB 없이 비즈니스 로직만 격리 검증
    // @SQLRestriction 동작(soft delete 필터)은 재현하지 않음
    //   → 해당 동작은 ProductServiceIntegrationTest에서 실제 DB로만 검증
    // ───────────────────────────────────────────────
    private static class FakeProductRepository implements ProductRepository {

        private final Map<Long, ProductModel> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public ProductModel save(ProductModel product) {
            setId(product, sequence++);
            store.put(product.getId(), product);
            return product;
        }

        @Override
        public Optional<ProductModel> findById(Long id) {
            return Optional.ofNullable(store.get(id))
                .filter(p -> p.getDeletedAt() == null);
        }

        @Override
        public Optional<ProductModel> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<ProductModel> findAllByBrandId(Long brandId) {
            return store.values().stream().filter(p -> p.getBrandId().equals(brandId)).toList();
        }

        @Override
        public Page<ProductModel> findAll(Long brandId, ProductSort sort, PageRequest pageRequest) {
            List<ProductModel> filtered = store.values().stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> brandId == null || p.getBrandId().equals(brandId))
                .sorted(comparatorFor(sort))
                .toList();

            int start = (int) pageRequest.getOffset();
            int end = Math.min(start + pageRequest.getPageSize(), filtered.size());
            List<ProductModel> content = start >= filtered.size() ? new ArrayList<>() : filtered.subList(start, end);

            return new org.springframework.data.domain.PageImpl<>(content, pageRequest, filtered.size());
        }

        /** soft delete 필터를 무시하고 조회 — 삭제 여부 검증용 */
        public Optional<ProductModel> findByIdIgnoringFilter(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        private Comparator<ProductModel> comparatorFor(ProductSort sort) {
            return switch (sort) {
                case PRICE_ASC -> Comparator.comparingLong(ProductModel::getPrice);
                case LIKES_DESC -> Comparator.comparingInt(ProductModel::getLikeCount).reversed();
                case LATEST -> Comparator.comparingLong(ProductModel::getId).reversed();
            };
        }

        private void setId(ProductModel product, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(product, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

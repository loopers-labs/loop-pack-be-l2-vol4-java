package com.loopers.application.order;

import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductFilter;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private OrderService orderService;
    private FakeOrderRepository fakeOrderRepository;
    private FakeProductRepository fakeProductRepository;
    private FakeStockRepository fakeStockRepository;

    @BeforeEach
    void setUp() {
        fakeOrderRepository = new FakeOrderRepository();
        fakeProductRepository = new FakeProductRepository();
        fakeStockRepository = new FakeStockRepository();
        orderService = new OrderService(fakeOrderRepository, fakeProductRepository, fakeStockRepository, new OrderDomainService());
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 요청이면, Order와 OrderItem이 저장된다.")
        @Test
        void create_savesOrderAndItems() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 10));

            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 2));

            // act
            OrderModel saved = orderService.create(1L, items, null, 20000L, 0L);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(saved.getTotalPrice()).isEqualTo(20000L);
            assertThat(fakeOrderRepository.findItemsByOrderId(saved.getId())).hasSize(1);
        }

        @DisplayName("존재하지 않는 상품으로 주문하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void create_throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long nonExistentProductId = 999L;
            List<OrderItemCommand> items = List.of(new OrderItemCommand(nonExistentProductId, 1));

            // act & assert
            assertThatThrownBy(() -> orderService.create(1L, items, null, 0L, 0L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족한 상품으로 주문하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 1));

            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 5));

            // act & assert
            assertThatThrownBy(() -> orderService.create(1L, items, null, 10000L, 0L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("다른 회원의 주문을 취소하면, FORBIDDEN 예외가 발생한다.")
        @Test
        void cancel_throwsException_whenNotOwner() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 10));
            OrderModel saved = orderService.create(1L, List.of(new OrderItemCommand(product.getId(), 1)), null, 10000L, 0L);

            // act & assert
            assertThatThrownBy(() -> orderService.cancel(saved.getId(), 2L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 취소된 주문을 취소하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void cancel_throwsException_whenAlreadyCancelled() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 10));
            OrderModel saved = orderService.create(1L, List.of(new OrderItemCommand(product.getId(), 1)), null, 10000L, 0L);
            orderService.cancel(saved.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> orderService.cancel(saved.getId(), 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태의 주문을 확정하면, CONFIRMED로 변경된다.")
        @Test
        void confirm_changesStatusToConfirmed() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 10));
            OrderModel saved = orderService.create(1L, List.of(new OrderItemCommand(product.getId(), 1)), null, 10000L, 0L);

            // act
            orderService.confirm(saved.getId(), 1L);

            // assert
            OrderModel confirmed = fakeOrderRepository.findById(saved.getId()).orElseThrow();
            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @DisplayName("주문 목록을 조회할 때,")
    @Nested
    class GetOrders {

        @DisplayName("memberId로 조회하면, 해당 회원의 주문만 반환된다.")
        @Test
        void getOrders_returnsOnlyMemberOrders() {
            // arrange
            ProductModel product = fakeProductRepository.save(new ProductModel("에어포스1", 10000L, 1L));
            fakeStockRepository.save(new StockModel(product.getId(), 10));
            orderService.create(1L, List.of(new OrderItemCommand(product.getId(), 1)), null, 10000L, 0L);
            orderService.create(2L, List.of(new OrderItemCommand(product.getId(), 1)), null, 10000L, 0L);

            // act
            List<OrderModel> orders = orderService.getOrders(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            // assert
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getMemberId()).isEqualTo(1L);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Fake Repositories
    // ─────────────────────────────────────────────────────────

    private static class FakeOrderRepository implements OrderRepository {

        private final Map<Long, OrderModel> orderStore = new HashMap<>();
        private final Map<Long, List<OrderItemModel>> itemStore = new HashMap<>();
        private final AtomicLong orderSeq = new AtomicLong(1L);

        @Override
        public OrderModel save(OrderModel order) {
            setId(order, orderSeq.getAndIncrement());
            orderStore.put(order.getId(), order);
            return order;
        }

        @Override
        public OrderItemModel saveItem(OrderItemModel item) {
            itemStore.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
            return item;
        }

        @Override
        public Optional<OrderModel> findById(Long id) {
            return Optional.ofNullable(orderStore.get(id))
                .filter(o -> o.getDeletedAt() == null);
        }

        @Override
        public Optional<OrderModel> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public List<OrderModel> findAllByDateRange(LocalDate startAt, LocalDate endAt) {
            return orderStore.values().stream().filter(o -> o.getDeletedAt() == null).toList();
        }

        @Override
        public List<OrderModel> findAllByMemberIdAndDateRange(Long memberId, LocalDate startAt, LocalDate endAt) {
            return orderStore.values().stream()
                .filter(o -> o.getMemberId().equals(memberId))
                .filter(o -> o.getDeletedAt() == null)
                .toList();
        }

        @Override
        public List<OrderItemModel> findItemsByOrderId(Long orderId) {
            return itemStore.getOrDefault(orderId, List.of());
        }

        private void setId(OrderModel order, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(order, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class FakeProductRepository implements ProductRepository {

        private final Map<Long, ProductModel> store = new HashMap<>();
        private final AtomicLong seq = new AtomicLong(1L);

        @Override
        public ProductModel save(ProductModel product) {
            setId(product, seq.getAndIncrement());
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
        public Page<ProductModel> findAll(ProductFilter filter, ProductSort sort, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProductModel> findAllByIds(List<Long> ids) {
            return ids.stream().map(store::get).filter(p -> p != null && p.getDeletedAt() == null).toList();
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

    private static class FakeStockRepository implements StockRepository {

        private final Map<Long, StockModel> store = new HashMap<>();

        @Override
        public StockModel save(StockModel stock) {
            store.put(stock.getProductId(), stock);
            return stock;
        }

        @Override
        public Optional<StockModel> findByProductId(Long productId) {
            return Optional.ofNullable(store.get(productId))
                .filter(s -> s.getDeletedAt() == null);
        }

        @Override
        public Optional<StockModel> findByProductIdForUpdate(Long productId) {
            return findByProductId(productId);
        }
    }
}

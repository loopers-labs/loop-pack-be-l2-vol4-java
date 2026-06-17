package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService orderService;
    private FakeOrderRepository orderRepository;
    private FakeProductRepository productRepository;
    private FakeUserRepository userRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new FakeOrderRepository();
        productRepository = new FakeProductRepository();
        userRepository = new FakeUserRepository();
        orderService = new OrderService(orderRepository, productRepository, userRepository);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 유저와 상품이 있으면 주문이 생성되고 재고가 차감된다.")
        @Test
        void createsOrder_andDecreasesStock() {
            userRepository.addUser(1L, new UserModel("user1", "pw1!", "user@email.com", "유저"));
            productRepository.addProduct(1L, new ProductModel("상품A", "설명", 5000L, 10, 1L));

            List<OrderService.OrderItemCommand> items = List.of(new OrderService.OrderItemCommand(1L, 3));
            Order order = orderService.createOrder(1L, items);

            assertAll(
                () -> assertThat(order).isNotNull(),
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(productRepository.findById(1L).get().getStock()).isEqualTo(7)
            );
        }

        @DisplayName("존재하지 않는 유저이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            productRepository.addProduct(1L, new ProductModel("상품A", "설명", 5000L, 10, 1L));
            List<OrderService.OrderItemCommand> items = List.of(new OrderService.OrderItemCommand(1L, 1));

            CoreException ex = assertThrows(CoreException.class, () -> orderService.createOrder(999L, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            userRepository.addUser(1L, new UserModel("user1", "pw1!", "user@email.com", "유저"));
            productRepository.addProduct(1L, new ProductModel("상품A", "설명", 5000L, 2, 1L));

            List<OrderService.OrderItemCommand> items = List.of(new OrderService.OrderItemCommand(1L, 5));

            CoreException ex = assertThrows(CoreException.class, () -> orderService.createOrder(1L, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            userRepository.addUser(1L, new UserModel("user1", "pw1!", "user@email.com", "유저"));
            List<OrderService.OrderItemCommand> items = List.of(new OrderService.OrderItemCommand(999L, 1));

            CoreException ex = assertThrows(CoreException.class, () -> orderService.createOrder(1L, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    static class FakeOrderRepository implements OrderRepository {
        private final List<Order> store = new ArrayList<>();
        private long idSeq = 1L;

        @Override
        public Order save(Order order) { store.add(order); return order; }

        @Override
        public Optional<Order> findById(Long id) {
            return store.stream().filter(o -> idSeq-- == id).findFirst();
        }

        @Override
        public List<Order> findByUserId(Long userId, String startAt, String endAt) {
            return store.stream().filter(o -> Objects.equals(o.getUserId(), userId)).toList();
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, ProductModel> store = new HashMap<>();

        public void addProduct(Long id, ProductModel product) { store.put(id, product); }

        @Override
        public ProductModel save(ProductModel product) { return product; }

        @Override
        public Optional<ProductModel> findById(Long id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable) {
            return new PageImpl<>(new ArrayList<>(store.values()));
        }

        @Override
        public void delete(Long id) { store.remove(id); }

        @Override
        public boolean existsById(Long id) { return store.containsKey(id); }
    }

    static class FakeUserRepository implements UserRepository {
        private final Map<Long, UserModel> store = new HashMap<>();

        public void addUser(Long id, UserModel user) { store.put(id, user); }

        @Override
        public UserModel save(UserModel user) { return user; }

        @Override
        public Optional<UserModel> findById(Long id) { return Optional.ofNullable(store.get(id)); }

        @Override
        public Optional<UserModel> findByLoginId(String loginId) {
            return store.values().stream().filter(u -> u.getLoginId().equals(loginId)).findFirst();
        }

        @Override
        public boolean existsByLoginId(String loginId) {
            return store.values().stream().anyMatch(u -> u.getLoginId().equals(loginId));
        }
    }
}

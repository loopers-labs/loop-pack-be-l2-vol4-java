package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartServiceTest {

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(new FakeCartItemRepository());
    }

    @DisplayName("장바구니에 상품을 추가할 때,")
    @Nested
    class AddOrIncrease {

        @DisplayName("처음 담는 상품이면 새 항목이 생성된다.")
        @Test
        void creates_whenProductNotInCart() {
            CartItem result = cartService.addOrIncrease(1L, 10L, 2);
            assertThat(result.getQuantity()).isEqualTo(2);
            assertThat(result.getProductId()).isEqualTo(10L);
        }

        @DisplayName("이미 담긴 상품이면 수량이 증가한다.")
        @Test
        void increases_whenProductAlreadyInCart() {
            cartService.addOrIncrease(1L, 10L, 2);
            CartItem result = cartService.addOrIncrease(1L, 10L, 3);
            assertThat(result.getQuantity()).isEqualTo(5);
        }

        @DisplayName("다른 유저의 동일 상품은 별도 항목으로 생성된다.")
        @Test
        void creates_separately_forDifferentUsers() {
            cartService.addOrIncrease(1L, 10L, 2);
            CartItem result = cartService.addOrIncrease(2L, 10L, 3);
            assertThat(result.getQuantity()).isEqualTo(3);
            assertThat(result.getUserId()).isEqualTo(2L);
        }
    }

    @DisplayName("장바구니 목록을 조회할 때,")
    @Nested
    class GetCartItems {

        @DisplayName("해당 유저의 항목만 반환한다.")
        @Test
        void returnsOnlyUserItems() {
            cartService.addOrIncrease(1L, 10L, 2);
            cartService.addOrIncrease(1L, 20L, 1);
            cartService.addOrIncrease(2L, 10L, 3);

            List<CartItem> items = cartService.getCartItems(1L);
            assertThat(items).hasSize(2);
            assertThat(items).allMatch(item -> item.getUserId().equals(1L));
        }

        @DisplayName("장바구니가 비어있으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenCartIsEmpty() {
            assertThat(cartService.getCartItems(1L)).isEmpty();
        }
    }

    @DisplayName("수량을 변경할 때,")
    @Nested
    class UpdateQuantity {

        @DisplayName("본인 항목이면 수량이 변경된다.")
        @Test
        void updates_whenOwner() {
            CartItem item = cartService.addOrIncrease(1L, 10L, 2);
            CartItem updated = cartService.updateQuantity(item.getId(), 1L, 5);
            assertThat(updated.getQuantity()).isEqualTo(5);
        }

        @DisplayName("타인 항목을 변경하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotOwner() {
            CartItem item = cartService.addOrIncrease(1L, 10L, 2);
            CoreException result = assertThrows(CoreException.class,
                () -> cartService.updateQuantity(item.getId(), 2L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 항목이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenItemNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> cartService.updateQuantity(999L, 1L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("항목을 삭제할 때,")
    @Nested
    class RemoveItem {

        @DisplayName("본인 항목이면 삭제된다.")
        @Test
        void removes_whenOwner() {
            CartItem item = cartService.addOrIncrease(1L, 10L, 2);
            cartService.removeItem(item.getId(), 1L);
            assertThat(cartService.getCartItems(1L)).isEmpty();
        }

        @DisplayName("타인 항목을 삭제하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotOwner() {
            CartItem item = cartService.addOrIncrease(1L, 10L, 2);
            CoreException result = assertThrows(CoreException.class,
                () -> cartService.removeItem(item.getId(), 2L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("장바구니를 비울 때,")
    @Nested
    class ClearCart {

        @DisplayName("해당 유저의 모든 항목이 삭제된다.")
        @Test
        void clearsAllItemsForUser() {
            cartService.addOrIncrease(1L, 10L, 2);
            cartService.addOrIncrease(1L, 20L, 1);
            cartService.addOrIncrease(2L, 10L, 3);

            cartService.clearCart(1L);

            assertThat(cartService.getCartItems(1L)).isEmpty();
            assertThat(cartService.getCartItems(2L)).hasSize(1);
        }
    }

    static class FakeCartItemRepository implements CartItemRepository {
        private final Map<Long, CartItem> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public CartItem save(CartItem cartItem) {
            if (cartItem.getId() == 0L) {
                ReflectionTestUtils.setField(cartItem, "id", idSequence.getAndIncrement());
            }
            store.put(cartItem.getId(), cartItem);
            return cartItem;
        }

        @Override
        public Optional<CartItem> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId) {
            return store.values().stream()
                .filter(i -> i.getUserId().equals(userId) && i.getProductId().equals(productId))
                .findFirst();
        }

        @Override
        public List<CartItem> findAllByUserId(Long userId) {
            return store.values().stream()
                .filter(i -> i.getUserId().equals(userId))
                .toList();
        }

        @Override
        public void delete(Long id) {
            store.remove(id);
        }

        @Override
        public void deleteAllByUserId(Long userId) {
            store.values().removeIf(i -> i.getUserId().equals(userId));
        }
    }
}

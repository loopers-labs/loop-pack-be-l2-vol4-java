package com.loopers.domain.wishlist;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WishlistServiceUnitTest {

    private InMemoryWishlistRepository wishlistRepository;
    private WishlistService sut;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @BeforeEach
    void setUp() {
        wishlistRepository = new InMemoryWishlistRepository();
        sut = new WishlistService(wishlistRepository);
    }

    private void saveDefaultWishlist() {
        wishlistRepository.save(new WishlistModel(USER_ID, PRODUCT_ID));
    }

    @DisplayName("찜 추가 시,")
    @Nested
    class Add {

        @DisplayName("유효한 입력이면, 찜이 등록된다.")
        @Test
        void returnsWishlist_whenInputsAreValid() {
            WishlistModel result = sut.add(USER_ID, PRODUCT_ID);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("이미 찜한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyWishlisted() {
            saveDefaultWishlist();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.add(USER_ID, PRODUCT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("찜 삭제 시,")
    @Nested
    class Remove {

        @DisplayName("찜 목록에 존재하는 상품이면, 찜이 삭제된다.")
        @Test
        void removesWishlist_whenWishlistExists() {
            saveDefaultWishlist();

            sut.remove(USER_ID, PRODUCT_ID);

            List<WishlistModel> result = sut.getList(USER_ID);
            assertThat(result).isEmpty();
        }

        @DisplayName("찜 목록에 존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenWishlistDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.remove(USER_ID, PRODUCT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("찜 목록 조회 시,")
    @Nested
    class GetList {

        @DisplayName("찜한 상품이 있으면, 해당 사용자의 찜 목록을 반환한다.")
        @Test
        void returnsWishlist_whenUserHasWishlists() {
            saveDefaultWishlist();
            wishlistRepository.save(new WishlistModel(USER_ID, 200L));

            List<WishlistModel> result = sut.getList(USER_ID);

            assertThat(result).hasSize(2);
        }

        @DisplayName("찜한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoWishlists() {
            List<WishlistModel> result = sut.getList(USER_ID);

            assertThat(result).isEmpty();
        }
    }
}

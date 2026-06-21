package com.loopers.domain.wishlist;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class WishlistServiceIntegrationTest {

    @Autowired private WishlistService wishlistService;
    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long OTHER_PRODUCT_ID = 200L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveWishlist(Long userId, Long productId) {
        wishlistRepository.save(new WishlistModel(userId, productId));
    }

    @DisplayName("찜 추가 시,")
    @Nested
    class Add {

        @DisplayName("유효한 입력이면, 찜이 등록된다.")
        @Test
        void returnsWishlist_whenInputsAreValid() {
            WishlistModel result = wishlistService.add(USER_ID, PRODUCT_ID);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        }
    }

    @DisplayName("찜 삭제 시,")
    @Nested
    class Remove {

        @DisplayName("찜 목록에 존재하는 상품이면, 찜이 삭제된다.")
        @Test
        void removesWishlist_whenWishlistExists() {
            saveWishlist(USER_ID, PRODUCT_ID);

            wishlistService.remove(USER_ID, PRODUCT_ID);

            assertThat(wishlistRepository.findAllByUserId(USER_ID)).isEmpty();
        }

        @DisplayName("찜 목록에 존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenWishlistDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> wishlistService.remove(USER_ID, PRODUCT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("찜 목록 조회 시,")
    @Nested
    class GetList {

        @DisplayName("찜한 상품이 있으면, 해당 사용자의 찜 목록을 반환한다.")
        @Test
        void returnsWishlist_whenUserHasWishlists() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(USER_ID, OTHER_PRODUCT_ID);

            List<WishlistModel> result = wishlistService.getList(USER_ID);

            assertThat(result).hasSize(2);
        }

        @DisplayName("찜한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoWishlists() {
            List<WishlistModel> result = wishlistService.getList(USER_ID);

            assertThat(result).isEmpty();
        }

        @DisplayName("다른 사용자의 찜은 반환되지 않는다.")
        @Test
        void excludesOtherUsersWishlists() {
            saveWishlist(USER_ID, PRODUCT_ID);
            saveWishlist(OTHER_USER_ID, OTHER_PRODUCT_ID);

            List<WishlistModel> result = wishlistService.getList(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        }
    }

}

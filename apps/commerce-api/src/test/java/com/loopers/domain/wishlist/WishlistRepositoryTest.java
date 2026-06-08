package com.loopers.domain.wishlist;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class WishlistRepositoryTest {

    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 userId, productId로 저장하면, DataIntegrityViolationException 이 발생한다.")
    @Test
    void throwsException_whenDuplicateUserIdAndProductIdIsInserted() {
        wishlistRepository.save(new WishlistModel(USER_ID, PRODUCT_ID));

        assertThrows(DataIntegrityViolationException.class, () ->
                wishlistRepository.save(new WishlistModel(USER_ID, PRODUCT_ID))
        );
    }
}

package com.loopers.application.product;

import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeCountFlushServiceTest {

    @Mock
    private ProductLikeCountRepository productLikeCountRepository;

    @Mock
    private ProductService productService;

    @DisplayName("Redis dirty 좋아요 카운터를 product.like_count 스냅샷으로 반영한다.")
    @Test
    void flushesDirtyLikeCountsToProductSnapshot() {
        // arrange
        ProductLikeCountFlushService flushService = new ProductLikeCountFlushService(
            productLikeCountRepository,
            productService
        );
        when(productLikeCountRepository.getDirtyCounts(500))
            .thenReturn(List.of(new ProductLikeCountSnapshot(1L, 12)));

        // act
        flushService.flushDirtyLikeCounts();

        // assert
        InOrder inOrder = inOrder(productService, productLikeCountRepository);
        inOrder.verify(productService).updateLikeCountSnapshot(1L, 12);
        inOrder.verify(productLikeCountRepository).clearDirty(1L);
    }
}

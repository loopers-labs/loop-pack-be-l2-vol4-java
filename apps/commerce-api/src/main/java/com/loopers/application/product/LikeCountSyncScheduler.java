package com.loopers.application.product;

import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountSyncScheduler {

    private static final String PENDING_KEY_PATTERN = "product:like:pending:*";
    private static final String PENDING_KEY_PREFIX = "product:like:pending:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    @Scheduled(fixedDelay = 300_000)
    public void productLikeSync() {
        ScanOptions options = ScanOptions.scanOptions().match(PENDING_KEY_PATTERN).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = redisTemplate.opsForValue().getAndDelete(key);
                if (value == null) {
                    continue;
                }
                long pending = Long.parseLong(value);
                if (pending == 0) {
                    continue;
                }
                Long productId = Long.parseLong(key.substring(PENDING_KEY_PREFIX.length()));
                applyToDatabase(productId, pending);
            }
        }
    }

    private void applyToDatabase(Long productId, long pending) {
        productRepository.adjustLikeCount(productId, pending);
    }
}
package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RankingApplicationService {

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(LocalDate date, int page, int size) {
        List<RankingEntry> entries = rankingRepository.getRankings(date, page, size);
        if (entries.isEmpty()) {
            return fallbackToPopularProducts(page, size);
        }

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductModel> productsById = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<Long> brandIds = productsById.values().stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandsById = brandRepository.findAllByIds(brandIds);

        return entries.stream()
            .filter(entry -> productsById.containsKey(entry.productId()))
            .map(entry -> {
                ProductModel product = productsById.get(entry.productId());
                BrandModel brand = brandsById.get(product.getBrandId());
                return new RankingInfo(
                    entry.rank(),
                    product.getId(),
                    product.getName(),
                    brand != null ? brand.getName() : null,
                    product.getPrice(),
                    entry.score(),
                    false
                );
            })
            .toList();
    }

    /**
     * 아직 랭킹 집계가 쌓이지 않은 날짜(예: 서비스 오픈 초기)를 위한 대체 목록.
     * 실시간 집계가 아니라 누적 좋아요순이므로 score는 의미 없는 값(0)이고 fallback=true로 구분된다.
     */
    private List<RankingInfo> fallbackToPopularProducts(int page, int size) {
        List<ProductModel> products = productRepository.findAllOrderByLikeCountDesc(null, PageRequest.of(page - 1, size));
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandsById = brandRepository.findAllByIds(brandIds);

        AtomicLong rank = new AtomicLong((long) (page - 1) * size + 1);
        return products.stream()
            .map(product -> {
                BrandModel brand = brandsById.get(product.getBrandId());
                return new RankingInfo(
                    rank.getAndIncrement(),
                    product.getId(),
                    product.getName(),
                    brand != null ? brand.getName() : null,
                    product.getPrice(),
                    0.0,
                    true
                );
            })
            .toList();
    }
}

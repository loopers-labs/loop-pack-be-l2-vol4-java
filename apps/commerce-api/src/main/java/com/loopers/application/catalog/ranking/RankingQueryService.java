package com.loopers.application.catalog.ranking;

import com.loopers.application.catalog.product.ProductResult;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.ranking.ProductRankMvRepository;
import com.loopers.domain.catalog.ranking.RankingPeriod;
import com.loopers.domain.catalog.ranking.RankingPeriodRange;
import com.loopers.domain.catalog.ranking.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RankingQueryService {

    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final ProductRankMvRepository productRankMvRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductLikeRepository productLikeRepository;

    @Transactional(readOnly = true)
    public PageResult<RankingResult> getRankings(RankingQuery.Search query) {
        LocalDate date = query.date() == null ? LocalDate.now(RANKING_ZONE) : query.date();
        List<RankingResult> visibleRankings = query.period() == RankingPeriod.DAILY
            ? getVisibleDailyRankings(date, query.userId(), query.size())
            : getVisibleMaterializedRankings(query.period(), date, query.userId());

        return page(visibleRankings, query.page(), query.size());
    }

    private PageResult<RankingResult> page(List<RankingResult> visibleRankings, int page, int size) {
        int fromIndex = Math.min(page * size, visibleRankings.size());
        int toIndex = Math.min(fromIndex + size, visibleRankings.size());
        return PageResult.of(
            visibleRankings.subList(fromIndex, toIndex),
            page,
            size,
            visibleRankings.size()
        );
    }

    private List<RankingResult> getVisibleDailyRankings(LocalDate date, String userId, int size) {
        long totalRankingCount = rankingRepository.count(date);
        if (totalRankingCount == 0) {
            return List.of();
        }

        int fetchSize = Math.max(size, 100);
        List<RankingResult> visibleRankings = new ArrayList<>();
        int page = 0;
        while ((long) page * fetchSize < totalRankingCount) {
            List<RankingRepository.Entry> rankingEntries = rankingRepository.findRankings(date, page, fetchSize);
            if (rankingEntries.isEmpty()) {
                break;
            }
            visibleRankings.addAll(toVisibleResults(rankingEntries, userId));
            page++;
        }

        return visibleRankings;
    }

    private List<RankingResult> getVisibleMaterializedRankings(RankingPeriod period, LocalDate date, String userId) {
        RankingPeriodRange range = RankingPeriodRange.of(period, date);
        long totalRankingCount = productRankMvRepository.count(period, range.startDate(), range.endDate());
        if (totalRankingCount == 0) {
            return List.of();
        }

        int fetchSize = Math.toIntExact(Math.min(totalRankingCount, 100L));
        List<RankingRepository.Entry> rankingEntries = productRankMvRepository.findRankings(
            period,
            range.startDate(),
            range.endDate(),
            0,
            fetchSize
        );
        return toVisibleResults(rankingEntries, userId);
    }

    private List<RankingResult> toVisibleResults(List<RankingRepository.Entry> rankingEntries, String userId) {
        Map<Long, Product> products = getProducts(rankingEntries.stream()
            .map(RankingRepository.Entry::productId)
            .toList());
        Map<Long, Brand> brands = getBrands(products.values().stream().map(Product::getBrandId).toList());
        Set<Long> likedProductIds = getLikedProductIds(userId, products.keySet());

        return rankingEntries.stream()
            .map(entry -> toResult(entry, products, brands, likedProductIds))
            .flatMap(Optional::stream)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Long> getTodayRank(Long productId) {
        return rankingRepository.findRank(LocalDate.now(RANKING_ZONE), productId)
            .map(RankingRepository.Entry::rank);
    }

    private Optional<RankingResult> toResult(
        RankingRepository.Entry entry,
        Map<Long, Product> products,
        Map<Long, Brand> brands,
        Set<Long> likedProductIds
    ) {
        Product product = products.get(entry.productId());
        if (product == null || !product.isOnSale()) {
            return Optional.empty();
        }

        Brand brand = brands.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
        }

        return Optional.of(new RankingResult(
            entry.rank(),
            entry.score(),
            ProductResult.from(product, brand, likedProductIds.contains(product.getId()))
        ));
    }

    private Map<Long, Product> getProducts(Collection<Long> productIds) {
        return productRepository.findAllByIds(productIds.stream().distinct().toList())
            .stream()
            .filter(product -> product != null && product.isOnSale())
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<Long, Brand> getBrands(Collection<Long> brandIds) {
        return brandRepository.findAllByIds(brandIds.stream().distinct().toList())
            .stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private Set<Long> getLikedProductIds(String userId, Collection<Long> productIds) {
        if (userId == null || userId.isBlank() || productIds.isEmpty()) {
            return Set.of();
        }

        return productLikeRepository.findLikedProductIds(userId, productIds);
    }
}

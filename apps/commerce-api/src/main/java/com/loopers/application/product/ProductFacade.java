package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.product.LikesCursor;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.productrank.ProductRankRepository;
import com.loopers.domain.productrank.RankedProduct;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeCountRepository likeCountRepository;
    private final ProductRankRepository productRankRepository;

    private static final int OVER_FETCH = 2; // 삭제/누락 보정용 여유분 배수

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price, stock));
        return ProductInfo.from(product, 0L);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(loadProduct(id), likeCountOf(id));
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long id) {
        ProductModel product = loadProduct(id);
        BrandModel brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return ProductDetailInfo.from(product, brand, likeCountOf(id));
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다.");
        }
        List<ProductModel> products = productRepository.findAll(brandId, ProductSortType.from(sort), page, size);
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, Long> counts = likeCountRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(ProductLikeCount::getProductId, ProductLikeCount::getCount));
        return products.stream()
            .map(product -> ProductInfo.from(product, counts.getOrDefault(product.getId(), 0L)))
            .toList();
    }

    /**
     * likes_desc 키셋 커서 조회(읽기모델 b). rank 가 stale 라 삭제/누락 상품이 섞일 수 있어
     * 여유분(OVER_FETCH 배)을 읽어 필터하고 size 를 채운다. 커서·표시 like_count 는 모두 rank 값.
     */
    @Transactional(readOnly = true)
    public ProductCursorPage getProductsByLikesCursor(Long brandId, String cursorToken, int size) {
        if (size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size 가 올바르지 않습니다.");
        }
        LikesCursor cursor = LikesCursor.decode(cursorToken);
        Long lastLike = cursor == null ? null : cursor.likeCount();
        Long lastId = cursor == null ? null : cursor.productId();

        List<RankedProduct> ranked =
            productRankRepository.findRankedByBrandLikesDesc(brandId, lastLike, lastId, size * OVER_FETCH);

        List<ProductInfo> items = new ArrayList<>(size);
        RankedProduct lastRanked = null;
        for (RankedProduct r : ranked) {
            lastRanked = r;
            Optional<ProductModel> product = productRepository.find(r.productId()); // find = 미삭제만
            if (product.isEmpty()) {
                continue; // 삭제/누락 필터
            }
            items.add(ProductInfo.from(product.get(), r.likeCount()));
            if (items.size() == size) {
                break;
            }
        }

        String next = (items.size() < size || lastRanked == null)
            ? null // 다 못 채웠으면 뒤가 없는 마지막 페이지
            : new LikesCursor(lastRanked.likeCount(), lastRanked.productId()).encode();
        return new ProductCursorPage(items, next);
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = loadProduct(id);
        product.update(name, description, price, stock);
        return ProductInfo.from(productRepository.save(product), likeCountOf(id));
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = loadProduct(id);
        product.delete(); // soft delete
        productRepository.save(product);
    }

    private ProductModel loadProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    private long likeCountOf(Long productId) {
        return likeCountRepository.find(productId)
            .map(ProductLikeCount::getCount)
            .orElse(0L);
    }
}

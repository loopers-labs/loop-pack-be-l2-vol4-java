package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.loopers.support.page.PagePolicy;
import com.loopers.support.page.ProductCursorCodec;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCursorCodec cursorCodec;

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, String imageUrl, Long price) {
        ProductModel product = new ProductModel(brandId, name, description, imageUrl, price);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 대고객 조회: 활성 상품만 반환. 없거나 비활성이면 NOT_FOUND (01 §7.4, UC-04). */
    @Transactional(readOnly = true)
    public ProductModel getActiveProduct(Long id) {
        return productRepository.find(id)
            .filter(ProductModel::isActive)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    /** 활성 상품을 Optional로 — 목록 조합 시 비활성/부재를 조용히 제외하기 위함 (UC-07). */
    @Transactional(readOnly = true)
    public Optional<ProductModel> findActive(Long id) {
        return productRepository.find(id).filter(ProductModel::isActive);
    }

    /**
     * 활성 상품 목록 — 브랜드 필터·정렬·키셋(커서) 페이지 (UC-03).
     * 불투명 커서 문자열을 디코드해(요청 정렬과 일치 검증) 키셋 조회로 넘긴다. cursor가 비면 첫 페이지.
     * hasNext 판별을 위해 size+1 건까지 반환될 수 있다(잘라내기는 상위에서).
     */
    @Transactional(readOnly = true)
    public List<ProductModel> getProducts(Long brandId, ProductSortType sort, String cursor, int size) {
        PagePolicy.validateSize(size);
        ProductSortType effectiveSort = (sort == null) ? ProductSortType.LATEST : sort;
        ProductCursor decoded = decodeCursor(effectiveSort, cursor);
        return productRepository.findActivePage(brandId, effectiveSort, decoded, size);
    }

    /** 빈 커서는 첫 페이지(null). 위조/손상 커서는 codec이 BAD_REQUEST로 막고, 정렬 불일치도 BAD_REQUEST. */
    private ProductCursor decodeCursor(ProductSortType sort, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        ProductCursor decoded = cursorCodec.decode(cursor);
        if (decoded.sort() != sort) {
            throw new CoreException(ErrorType.BAD_REQUEST, "커서의 정렬 기준이 요청 정렬과 일치하지 않습니다.");
        }
        return decoded;
    }

    /** 특정 브랜드의 활성 상품 전체 (Brand→Product cascade 전파용 — 01 §7.5). */
    @Transactional(readOnly = true)
    public List<ProductModel> getActiveProductsByBrand(Long brandId) {
        return productRepository.findActiveByBrandId(brandId);
    }

    /** 활성 상품 batch 조회 — 좋아요한 상품 목록 조합 N+1 회피 (UC-07). */
    @Transactional(readOnly = true)
    public List<ProductModel> findActiveByIds(Collection<Long> ids) {
        return productRepository.findActiveByIds(ids);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, String imageUrl, Long price) {
        ProductModel product = getProduct(id);
        product.update(name, description, imageUrl, price);
        return productRepository.save(product);
    }

    /** soft delete (01 §7.5). 행은 남기고 deletedAt만 설정 → 조회·신규 좋아요에서 NOT_FOUND로 가려진다. */
    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        product.delete();
        productRepository.save(product);
    }

    /**
     * 좋아요 수 동기 +1 (01 §7.3, 04 §4.2 — 좋아요 등록과 동일 트랜잭션).
     * 원자적 UPDATE(likes_count = likes_count + 1)로 처리해 동시 좋아요의 lost update를 차단한다.
     */
    @Transactional
    public void increaseLikesCount(Long id) {
        productRepository.incrementLikesCount(id);
    }

    /** 좋아요 수 동기 -1. 원자적 UPDATE + likes_count > 0 가드로 음수를 방지한다. */
    @Transactional
    public void decreaseLikesCount(Long id) {
        productRepository.decrementLikesCount(id);
    }
}

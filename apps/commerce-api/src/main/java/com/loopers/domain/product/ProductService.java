package com.loopers.domain.product;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Product createProduct(Long brandId, String name, String description, long price) {
        Product product = Product.create(brandId, name, description, price);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findActiveById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    @Transactional(readOnly = true)
    public List<Product> getProducts(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findActiveAllByIds(productIds);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getProducts(PageQuery query, Long brandId) {
        return productRepository.findActiveAll(query, brandId);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getVisibleProducts(PageQuery query, Long brandId, ProductSort sort) {
        return productRepository.findVisibleAll(query, brandId, sort);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> getLikedProducts(Long userId, PageQuery query) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
        return productRepository.findVisibleLikedAllByUserId(userId, query);
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = getProduct(productId);
        product.delete();
    }

    @Transactional
    public void deleteProductsByBrandId(Long brandId) {
        productRepository.findActiveAllByBrandId(brandId)
            .forEach(Product::delete);
    }

    @Transactional
    public Product updateProduct(Long productId, String name, String description, long price) {
        Product product = getProduct(productId);
        product.update(name, description, price);
        return product;
    }
}

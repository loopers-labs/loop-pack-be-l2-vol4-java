package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCacheRepository;
import com.loopers.domain.product.ProductLikeCountRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductStock;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductLikeCountRepository productLikeCountRepository;
    private final ProductCacheRepository productCacheRepository;

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productCacheRepository.findById(id)
            .orElseGet(() -> {
                Product product = productRepository.find(id)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
                productCacheRepository.save(product);
                return product;
            });
    }

    @Transactional(readOnly = true)
    public ProductStock getProductStock(Long productId) {
        return productStockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    @Transactional(readOnly = true)
    public Page<Product> getProducts(Long brandId, ProductSort sort, Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        return productCacheRepository.findAll(brandId, sort, page, size)
            .orElseGet(() -> {
                Pageable p = PageRequest.of(page, size, toSort(sort));
                Page<Product> products = productRepository.findAll(brandId, p);
                productCacheRepository.saveAll(brandId, sort, page, size, products);
                return products;
            });
    }

    private Sort toSort(ProductSort sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    @Transactional
    public Product createProduct(Long brandId, String name, BigDecimal price, long stock) {
        Product product = new Product(brandId, name, price);
        Product saved = productRepository.save(product);
        productStockRepository.save(new ProductStock(saved.getId(), stock));
        productCacheRepository.evictAll();
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, Long brandId, String name, BigDecimal price, long stock) {
        Product product = productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        product.update(brandId, name, price);
        ProductStock productStock = getProductStock(id);
        productStock.updateQuantity(stock);
        productStockRepository.save(productStock);
        Product saved = productRepository.save(product);
        productCacheRepository.evict(id);
        productCacheRepository.evictAll();
        return saved;
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        product.delete();
        productRepository.save(product);
        productCacheRepository.evict(id);
        productCacheRepository.evictAll();
    }

    @Transactional
    public void deductStock(Long productId, long quantity) {
        ProductStock stock = productStockRepository.findByProductIdForUpdate(productId);
        stock.deduct(quantity);
        productStockRepository.save(stock);
    }

    public void incrementLikeCount(Long productId) {
        productLikeCountRepository.increment(productId);
    }

    public void decrementLikeCount(Long productId) {
        productLikeCountRepository.decrement(productId);
    }

    public void deleteAllByBrandId(Long brandId) {
        productRepository.deleteAllByBrandId(brandId);
    }
}
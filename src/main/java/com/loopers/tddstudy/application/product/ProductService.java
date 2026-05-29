package com.loopers.tddstudy.application.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    public Product create(String name, int price, int stock, Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
        if (brand.isDeleted()) {
            throw new IllegalArgumentException("삭제된 브랜드에는 상품을 등록할 수 없습니다.");
        }
        return productRepository.save(new Product(name, price, stock, brandId));
    }

    public void publish(Long id) {
        Product product = findOrThrow(id);
        product.publish();
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id, Long userId, String userRole) {
        Product product = findOrThrow(id);
        if (!isVisible(product, userId, userRole)) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<Product> getAll(Long userId, String userRole, Long brandId, String sort) {
        return productRepository.findAll().stream()
                .filter(p -> brandId == null || brandId.equals(p.getBrandId()))
                .filter(p -> isVisible(p, userId, userRole))
                .sorted(getComparator(sort))
                .collect(Collectors.toList());
    }

    private Comparator<Product> getComparator(String sort) {
        if ("price_asc".equals(sort)) {
            return Comparator.comparingInt(Product::getPrice);
        }
        if ("likes_desc".equals(sort)) {
            return Comparator.comparingLong(Product::getLikeCount).reversed();
        }
        // latest: createdAt desc, id desc (동시 생성 시 tiebreaker)
        return Comparator.comparing(Product::getCreatedAt)
                .thenComparing(Product::getId)
                .reversed();
    }



    public void update(Long id, String name, int price, int stock) {
        Product product = findOrThrow(id);
        product.update(name, price, stock);
        productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = findOrThrow(id);
        product.softDelete();
        productRepository.save(product);
    }

    public void deleteAllByBrandId(Long brandId) {
        productRepository.findAllByBrandId(brandId).forEach(product -> {
            product.softDelete();
            productRepository.save(product);
        });
    }

    private boolean isVisible(Product product, Long userId, String userRole) {
        if ("OPERATOR".equals(userRole)) return true;
        if (product.isDeleted()) return false;
        if (product.isActive()) return true;
        // DRAFT: 브랜드 소유자만 조회 가능
        return brandRepository.findById(product.getBrandId())
                .map(brand -> userId.equals(brand.getOwnerId()))
                .orElse(false);
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }
}

package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;
    private final ProductDomainService productDomainService;

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, int page, int size, String sort) {
        List<ProductModel> products;
        if ("likes_desc".equals(sort)) {
            products = productRepository.findAllOrderByLikeCountDesc(brandId, PageRequest.of(page, size));
        } else {
            products = productRepository.findAll(brandId, PageRequest.of(page, size, toSort(sort)));
        }

        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brands = brandRepository.findAllByIds(brandIds);
        Map<Long, Long> likeCounts = likeRepository.countByProductIds(productIds);

        return products.stream()
            .map(product -> ProductInfo.from(productDomainService.compose(
                product,
                brands.get(product.getBrandId()),
                likeCounts.getOrDefault(product.getId(), 0L)
            )))
            .toList();
    }

    private Sort toSort(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
        BrandModel brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        long likeCount = likeRepository.countByProductId(id);
        return ProductInfo.from(productDomainService.compose(product, brand, likeCount));
    }

    @Transactional(readOnly = true)
    public List<ProductAdminInfo> getProductsForAdmin(Long brandId, int page, int size) {
        return productRepository.findAll(brandId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream().map(ProductAdminInfo::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductAdminInfo getProductForAdmin(Long id) {
        return ProductAdminInfo.from(productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다.")));
    }

    @Transactional
    public ProductAdminInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = new ProductModel(brandId, name, description, price, stock);
        return ProductAdminInfo.from(productRepository.save(product));
    }

    @Transactional
    public ProductAdminInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
        product.update(name, description, price, stock);
        return ProductAdminInfo.from(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
        productRepository.delete(id);
    }
}

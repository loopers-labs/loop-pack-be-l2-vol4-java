package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.wishlist.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final WishlistService wishlistService;

    public Page<ProductInfo> getProducts(Long brandId, String sort, Pageable pageable) {
        Page<ProductModel> products = productService.getList(brandId, ProductSortType.from(sort), pageable);
        Map<Long, BrandModel> brandMap = batchBrands(products.getContent());
        Map<Long, Long> likeCounts = batchLikeCounts(products.getContent());
        return products.map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId()), likeCounts.getOrDefault(p.getId(), 0L)));
    }

    public ProductInfo getProduct(Long productId) {
        ProductModel product = productService.get(productId);
        BrandModel brand = brandService.get(product.getBrandId());
        long likeCount = wishlistService.countByProductId(productId);
        return ProductInfo.from(product, brand, likeCount);
    }

    public Page<ProductInfo> getAdminProducts(Long brandId, Pageable pageable) {
        Page<ProductModel> products = productService.getAdminList(brandId, pageable);
        Map<Long, BrandModel> brandMap = batchBrands(products.getContent());
        Map<Long, Long> likeCounts = batchLikeCounts(products.getContent());
        return products.map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId()), likeCounts.getOrDefault(p.getId(), 0L)));
    }

    public ProductInfo registerProduct(Long brandId, String name) {
        BrandModel brand = brandService.get(brandId);
        ProductModel product = productService.create(brand.getId(), new ProductName(name));
        return ProductInfo.from(product, brand, 0L);
    }

    public ProductInfo updateProduct(Long productId, String name) {
        ProductModel product = productService.update(productId, new ProductName(name));
        BrandModel brand = brandService.get(product.getBrandId());
        long likeCount = wishlistService.countByProductId(productId);
        return ProductInfo.from(product, brand, likeCount);
    }

    public void deleteProduct(Long productId) {
        productService.delete(productId);
    }

    private Map<Long, BrandModel> batchBrands(List<ProductModel> products) {
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        return brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, b -> b));
    }

    private Map<Long, Long> batchLikeCounts(List<ProductModel> products) {
        List<Long> ids = products.stream().map(ProductModel::getId).toList();
        return wishlistService.countsByProductIds(ids);
    }
}

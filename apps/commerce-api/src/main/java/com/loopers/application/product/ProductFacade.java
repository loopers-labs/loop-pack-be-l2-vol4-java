package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        Brand brand = brandService.getBrand(brandId);
        Product product = productService.createProduct(brandId, name, description, price, stock);
        return ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts() {
        return getAllProducts(null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(String sort) {
        return getAllProducts(null, sort, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        if (brandId != null) {
            brandService.getBrand(brandId);
        }

        List<Product> products = productService.getAllProducts(brandId, sort, page, size);
        List<Long> brandIds = productBrandProcessService.getBrandIds(products);
        List<Brand> brands = brandService.getBrandsByIds(brandIds);
        return productBrandProcessService.getProductDetailViews(products, brands).stream()
            .map(ProductInfo::from)
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, price, stock);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(productBrandProcessService.getProductDetailView(product, brand));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}

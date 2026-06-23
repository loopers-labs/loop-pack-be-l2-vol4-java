package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductPage;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandService brandService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.createProduct(brandId, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public ProductDisplayInfo getProductDisplay(Long id) {
        ProductModel product = productService.getProduct(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDisplayInfo.of(product, brand);
    }

    public ProductDetailInfo getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductDetailInfo.of(product, brand);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductPageInfo searchProducts(Long brandId, String sort, String direction, Integer page, Integer size) {
        ProductPage productPage = productService.searchProducts(brandId, sort, direction, page, size);

        // N+1 제거: 페이지 상품들의 brandId를 모아 브랜드를 한 번에 조회한 뒤 메모리에서 결합한다.
        // (상품마다 brandService.getBrand()를 호출하면 20건 조회 시 1 + 20 쿼리가 발생)
        List<Long> brandIds = productPage.products().stream()
            .map(ProductModel::getBrandId)
            .distinct()
            .toList();
        Map<Long, BrandModel> brandMap = brandService.getBrands(brandIds);

        List<ProductDisplayInfo> content = productPage.products().stream()
            .map(product -> {
                BrandModel brand = brandMap.get(product.getBrandId());
                if (brand == null) {
                    throw new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다.");
                }
                return ProductDisplayInfo.of(product, brand);
            })
            .toList();

        return new ProductPageInfo(
            content,
            productPage.page(),
            productPage.size(),
            productPage.totalElements(),
            productPage.totalPages()
        );
    }

    public ProductInfo updateProduct(Long id, Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, brandId, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}

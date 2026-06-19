package com.loopers.product.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.stock.domain.ProductStock;
import com.loopers.stock.domain.ProductStockService;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductAdminFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final ProductLikeSummaryWriter productLikeSummaryWriter;
    private final ProductDetailCacheEvictor productDetailCacheEvictor;

    @Transactional
    public ProductInfo createProduct(CreateProductCommand command) {
        Brand brand = brandService.getBrand(command.brandId());
        Product product = productService.createProduct(
            brand.getId(),
            command.name(),
            command.description(),
            command.price()
        );
        ProductStock productStock = productStockService.createProductStock(product.getId(), command.stockQuantity());
        productLikeSummaryWriter.initialize(product.getId(), product.getBrandId());
        productDetailCacheEvictor.evict(product.getId());
        return ProductInfo.from(product, productStock);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        Product product = productService.getProduct(productId);
        ProductStock productStock = productStockService.getProductStock(product.getId());
        return ProductInfo.from(product, productStock);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductInfo> getProducts(int page, int size, Long brandId) {
        PageResult<Product> products = productService.getProducts(new PageQuery(page, size), brandId);
        Map<Long, ProductStock> productStocks = productStockService.getProductStocks(
            products.content().stream()
                .map(Product::getId)
                .toList()
        );
        return products.map(product -> ProductInfo.from(product, getStock(productStocks, product.getId())));
    }

    private ProductStock getStock(Map<Long, ProductStock> productStocks, Long productId) {
        ProductStock productStock = productStocks.get(productId);
        if (productStock == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "상품 재고 정보가 존재하지 않습니다.");
        }
        return productStock;
    }

    public void deleteProduct(Long productId) {
        productService.deleteProduct(productId);
        productDetailCacheEvictor.evict(productId);
    }

    @Transactional
    public ProductInfo updateProduct(UpdateProductCommand command) {
        Product product = productService.updateProduct(
            command.productId(),
            command.name(),
            command.description(),
            command.price()
        );
        ProductStock productStock = productStockService.getProductStock(product.getId());
        if (command.hasStockChange()) {
            productStock = productStockService.changeProductStock(product.getId(), command.stockQuantity());
        }
        productDetailCacheEvictor.evict(product.getId());
        return ProductInfo.from(product, productStock);
    }
}

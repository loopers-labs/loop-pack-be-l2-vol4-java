package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStock;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
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

    @Transactional
    public void deleteProduct(Long productId) {
        productService.deleteProduct(productId);
    }

    @Transactional
    public ProductInfo updateProduct(UpdateProductCommand command) {
        Product product = productService.updateProduct(
            command.productId(),
            command.name(),
            command.description(),
            command.price()
        );
        ProductStock productStock = productStockService.changeProductStock(product.getId(), command.stockQuantity());
        return ProductInfo.from(product, productStock);
    }
}

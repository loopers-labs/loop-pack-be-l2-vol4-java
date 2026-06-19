package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final BrandRepository brandRepository;
    private final ProductDomainService productDomainService;

    @Transactional
    public ProductInfo create(ProductCreateCommand command) {
        BrandModel brand = brandRepository.findById(command.brandId())
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "등록되지 않은 브랜드입니다."));
        brand.validateActive();

        ProductModel product = productRepository.save(new ProductModel(brand, command.name(), command.price()));

        StockModel stock = new StockModel(product, command.initialStock());
        stockRepository.save(stock);

        ProductDetail detail = productDomainService.assembleDetail(product, stock);
        return ProductInfo.from(detail);
    }

    @Transactional(readOnly = true)
    public ProductInfo getById(Long id) {
        ProductModel product = productRepository.findActiveById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        StockModel stock = stockRepository.findByProductId(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));

        ProductDetail detail = productDomainService.assembleDetail(product, stock);
        return ProductInfo.from(detail);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getAll(Pageable pageable, ProductSearchCondition condition) {
        return productRepository.findAllActive(pageable, condition)
            .map(product -> {
                StockModel stock = stockRepository.findByProductId(product.getId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
                ProductDetail detail = productDomainService.assembleDetail(product, stock);
                return ProductInfo.from(detail);
            });
    }

    @Transactional
    public ProductInfo update(Long id, ProductUpdateCommand command) {
        ProductModel product = productRepository.findActiveById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.update(command.name(), command.price());
        productRepository.save(product);

        StockModel stock = stockRepository.findByProductId(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));

        ProductDetail detail = productDomainService.assembleDetail(product, stock);
        return ProductInfo.from(detail);
    }

    @Transactional
    public void delete(Long id) {
        ProductModel product = productRepository.findActiveById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.delete();
        productRepository.save(product);
    }
}

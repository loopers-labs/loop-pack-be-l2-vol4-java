package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductService {

  private final ProductRepository productRepository;

  @Transactional
  public ProductModel createProduct(
      String name, String description, Long price, Integer stock, Long brandId) {
    ProductModel product = new ProductModel(name, description, price, stock, brandId);
    return productRepository.save(product);
  }

  @Transactional(readOnly = true)
  public ProductModel getProduct(Long id) {
    return productRepository
        .find(id)
        .orElseThrow(
            () -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
  }

  @Transactional(readOnly = true)
  public List<ProductModel> getAllProducts() {
    return productRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<ProductModel> getActiveProducts(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return productRepository.findAllActiveByIds(ids);
  }

  @Transactional(readOnly = true)
  public ProductPage searchProducts(ProductSearchCondition condition) {
    return productRepository.search(condition);
  }

  @Transactional
  public ProductModel updateProduct(
      Long id, String name, String description, Long price, Integer stock, Long brandId) {
    ProductModel product = getProduct(id);
    product.update(name, description, price, stock, brandId);
    return productRepository.save(product);
  }

  @Transactional
  public void deleteProduct(Long id) {
    getProduct(id); // 존재 여부 확인
    productRepository.delete(id);
  }
}

package com.loopers.domain.stock;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.*;
import jakarta.persistence.*;
// Hides: non-negative stock invariants and decrement failure semantics.
@Entity @Table(name="stock") public class StockModel extends BaseEntity{
 private Long productId; private Integer quantity; protected StockModel(){}
 public StockModel(long productId,int quantity){if(productId<=0||quantity<0)throw new CoreException(ErrorType.BAD_REQUEST);this.productId=productId;this.quantity=quantity;}
 public void decrease(int amount){if(amount<=0)throw new CoreException(ErrorType.BAD_REQUEST);if(quantity<amount)throw new CoreException(ErrorType.CONFLICT,"insufficient stock");quantity-=amount;}
 public Integer getQuantity(){return quantity;} public Long getProductId(){return productId;}
}

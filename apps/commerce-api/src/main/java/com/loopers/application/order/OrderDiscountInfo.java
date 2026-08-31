package com.loopers.application.order;
import com.loopers.domain.order.Order;
public record OrderDiscountInfo(long orderId,long originalAmount,long discountAmount,long finalAmount,boolean confirmed){
 public static OrderDiscountInfo from(Order o){return new OrderDiscountInfo(o.getId(),o.getOriginalAmount(),o.getDiscountAmount(),o.getFinalAmount(),o.isConfirmed());}
}

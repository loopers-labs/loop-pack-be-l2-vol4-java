package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingDestination {

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "zipcode", nullable = false)
    private String zipcode;

    @Column(name = "address1", nullable = false)
    private String address1;

    @Column(name = "address2")
    private String address2;

    private ShippingDestination(String recipientName, String recipientPhone, String zipcode, String address1, String address2) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        validate();
    }

    public static ShippingDestination create(
            String recipientName, String recipientPhone, String zipcode, String address1, String address2
    ) {
        return new ShippingDestination(recipientName, recipientPhone, zipcode, address1, address2);
    }

    private void validate() {
        if (recipientName == null || recipientName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수령인명은 비어있을 수 없습니다.");
        }
        if (recipientPhone == null || recipientPhone.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "연락처는 비어있을 수 없습니다.");
        }
        if (zipcode == null || zipcode.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "우편번호는 비어있을 수 없습니다.");
        }
        if (address1 == null || address1.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기본주소는 비어있을 수 없습니다.");
        }
    }
}

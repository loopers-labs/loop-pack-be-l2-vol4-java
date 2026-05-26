package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class ShippingInfo {

    @Embedded
    private Receiver receiver;

    @Embedded
    private Address address;

    protected ShippingInfo() {}

    public ShippingInfo(Receiver receiver, Address address) {
        if (receiver == null) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (address == null) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.receiver = receiver;
        this.address = address;
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public Address getAddress() {
        return address;
    }
}

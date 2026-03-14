package org.n2n.learning.droolspoclocal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String customerId;
    private String customerType;  // "REGULAR", "PREMIUM", "VIP"
    private double orderAmount;
    private int itemCount;

    // Set by Drools rules
    private double discountPercentage;
    private double finalAmount;
    private String discountReason;
}

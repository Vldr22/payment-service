package org.resume.paymentservice.model.dto.data;

import lombok.Builder;
import lombok.Getter;
import org.resume.paymentservice.model.enums.Currency;

import java.math.BigDecimal;

@Getter
@Builder
public class BillingChargeData {
    private BigDecimal amount;
    private Currency currency;
    private String customerId;
    private String paymentMethodId;
    private String description;
    private Long subscriptionId;
    private String idempotencyKey;
}

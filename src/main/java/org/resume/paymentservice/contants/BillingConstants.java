package org.resume.paymentservice.contants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BillingConstants {

    public static final String METADATA_TYPE_BILLING = "billing";
    public static final String METADATA_KEY_TYPE = "type";
    public static final String METADATA_KEY_SUBSCRIPTION_ID = "subscriptionId";
    public static final String PAYMENT_FAILED_MESSAGE = "Payment failed via Stripe webhook";
    public static final String BILLING_PAYMENT_DESCRIPTION = "Billing: %s";
    public static final String IDEMPOTENCY_KEY_FORMAT = "sub_%d_%s";
}

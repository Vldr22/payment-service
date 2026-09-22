package org.resume.paymentservice.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessages {

    // User errors
    public static final String USER_NOT_FOUND_BY_ID = "User not found with id: ";
    public static final String USER_NOT_FOUND_BY_EMAIL = "User not found with email: ";
    public static final String USER_NOT_FOUND_BY_PHONE = "User not found with phone: ";
    public static final String PHONE_ALREADY_REGISTERED  = "Phone already registered: ";
    public static final String EMAIL_ALREADY_REGISTERED = "Email already registered: ";
    public static final String INVALID_CREDENTIALS = "Invalid email or password";

    // Payment errors
    public static final String PAYMENT_NOT_FOUND = "Payment doesn't exist with id: ";
    public static final String PAYMENT_NOT_FOUND_BY_STRIPE_ID = "Payment doesn't exist with Stripe Id: ";

    // Refund errors
    public static final String PAYMENT_NOT_REFUNDABLE = "Payment cannot be refunded. Current status: ";
    public static final String REFUND_ALREADY_REQUESTED = "Refund request already exists for payment: ";
    public static final String REFUND_NOT_FOUND = "Refund not found with id: ";
    public static final String STRIPE_REFUND_FAILED = "Stripe refund failed for payment: ";
    public static final String REFUND_NOT_PENDING = "Refund request cannot be processed. Current status: ";
    public static final String REFUND_NOT_FOUND_BY_PAYMENT_INTENT = "Approved refund not found for payment: ";

    // Stripe errors
    public static final String STRIPE_PAYMENT_CREATION_FAILED = "Stripe payment creation failed: ";
    public static final String STRIPE_PAYMENT_CONFIRMED_FAILED = "Stripe payment confirmed failed: ";
    public static final String STRIPE_PAYMENT_STATUS_Error = "Failed to get payment status from Stripe: ";

    // Card errors
    public static final String CARD_NOT_FOUND = "Card not found with id: ";
    public static final String CARD_ALREADY_ATTACHED = "Card already present: ";
    public static final String CARD_LINKED_TO_ACTIVE_SUBSCRIPTION = "Card is linked to active subscription: ";

    // Webhook errors
    public static final String WEBHOOK_INVALID_SIGNATURE = "Invalid webhook signature for this event: ";
    public static final String WEBHOOK_DESERIALIZATION_FAILED = "Can't deserialize PaymentIntent from event: ";
    public static final String WEBHOOK_NOT_FOUND_BY_ID = "Webhook not found with id: ";

    // Security errors
    public static final String TOKEN_REVOKED = "Token has been revoked";
    public static final String TOKEN_EXPIRED = "Token has expired";
    public static final String TOKEN_INVALID = "Invalid token";
    public static final String PAYMENT_ACCESS_DENIED = "You don't have access to this payment";
    public static final String PASSWORD_CHANGE_REQUIRED = "Password change required before login";

    // Validation errors
    public static final String INVALID_PHONE_NUMBER = "Invalid phone number";

    // Verification errors
    public static final String SMS_CODE_EXPIRED = "SMS code expired for: ";
    public static final String SMS_CODE_INVALID = "Invalid SMS code for: ";
    public static final String EMAIL_CODE_EXPIRED = "Email code expired for: ";
    public static final String EMAIL_CODE_INVALID = "Invalid email code for: ";

    // Subscription errors
    public static final String SUBSCRIPTION_NOT_FOUND_BY_ID = "Subscription not found with id: ";
    public static final String SUBSCRIPTION_NOT_FOUND_BY_USER_ID = "Subscription not found for userId: ";
    public static final String SUBSCRIPTION_ALREADY_EXISTS = "Subscription already exists for userId: ";

    // Billing errors
    public static final String BILLING_NOT_FOUND_BY_PAYMENT_INTENT = "Billing attempt not found for paymentIntentId: ";
}

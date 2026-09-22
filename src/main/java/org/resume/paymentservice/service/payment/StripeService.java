package org.resume.paymentservice.service.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.contants.BillingConstants;
import org.resume.paymentservice.exception.StripePaymentException;
import org.resume.paymentservice.model.dto.data.BillingChargeData;
import org.resume.paymentservice.model.dto.data.SavedCardData;
import org.resume.paymentservice.model.dto.request.CreatePaymentRequest;
import org.resume.paymentservice.model.dto.response.PaymentResponse;
import org.resume.paymentservice.model.enums.RefundReason;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class StripeService {

    public PaymentResponse createStripePayment(CreatePaymentRequest request, String customerId) {
        try {
            long amountInCents = convertToCents(request.amount());

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.currency().name().toLowerCase())
                    .setDescription(request.description())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    );

            if (customerId != null) {
                builder.setCustomer(customerId);
            }

            PaymentIntent paymentIntent = PaymentIntent.create(builder.build());

            log.info("Payment Stripe has been created: id={}, status={}",
                    paymentIntent.getId(), paymentIntent.getStatus());

            return toPaymentResponse(paymentIntent);

        } catch (StripeException e) {
            log.error("Stripe payment creation failed: {}", e.getMessage(), e);
            throw StripePaymentException.byCreationError(e.getMessage(), e);
        }
    }


    public String createRefund(String paymentIntentId, RefundReason reason) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setReason(mapRefundReason(reason))
                    .build();

            Refund stripeRefund = Refund.create(params);

            log.info("Stripe refund created: id={}, paymentIntent={}, status={}",
                    stripeRefund.getId(), paymentIntentId, stripeRefund.getStatus());

            return stripeRefund.getId();

        } catch (StripeException e) {
            log.error("Stripe refund failed: paymentIntent={}, error={}", paymentIntentId, e.getMessage());
            throw StripePaymentException.byRefundError(e.getMessage(), e);
        }
    }

    /**
     * Списывает по подписке сохранённой картой.
     * Повторный запрос с тем же ключом идемпотентности вернёт созданный ранее PaymentIntent.
     */
    public PaymentIntent chargeWithSavedCard(BillingChargeData data) {
        try {
            long amountInCents = convertToCents(data.getAmount());

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(data.getCurrency().name().toLowerCase())
                    .setCustomer(data.getCustomerId())
                    .setPaymentMethod(data.getPaymentMethodId())
                    .setDescription(data.getDescription())
                    .setConfirm(true)
                    .setOffSession(true)
                    .putMetadata(BillingConstants.METADATA_KEY_TYPE, BillingConstants.METADATA_TYPE_BILLING)
                    .putMetadata(BillingConstants.METADATA_KEY_SUBSCRIPTION_ID, data.getSubscriptionId().toString())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(data.getIdempotencyKey())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params, options);

            log.info("Billing charge created: stripePaymentIntentId={}, customerId={}, subscriptionId={}, status={}",
                    paymentIntent.getId(), data.getCustomerId(), data.getSubscriptionId(), paymentIntent.getStatus());

            return paymentIntent;

        } catch (StripeException e) {
            log.error("Billing charge failed: customerId={}, subscriptionId={}, error={}",
                    data.getCustomerId(), data.getSubscriptionId(), e.getMessage());
            throw StripePaymentException.byCreationError(e.getMessage(), e);
        }
    }

    public String createCustomer(String name, String phone) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(name)
                    .setPhone(phone)
                    .build();

            Customer customer = Customer.create(params);

            log.info("Stripe Customer created: customerId={}", customer.getId());
            return customer.getId();

        } catch (StripeException e) {
            log.error("Failed to create Stripe Customer: {}", e.getMessage(), e);
            throw StripePaymentException.byCreationError(e.getMessage(), e);
        }
    }

    public SavedCardData addPaymentMethod(String customerId, String paymentMethodId) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

            PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build();

            PaymentMethod attached = paymentMethod.attach(params);
            PaymentMethod.Card card = attached.getCard();

            log.info("PaymentMethod attached: customerId={}, paymentMethodId={}", customerId, paymentMethodId);

            return new SavedCardData(
                    attached.getId(),
                    card.getLast4(),
                    card.getBrand(),
                    card.getExpMonth().shortValue(),
                    card.getExpYear().shortValue()
            );

        } catch (StripeException e) {
            log.error("Failed to attach PaymentMethod: {}", e.getMessage(), e);
            throw StripePaymentException.byCreationError(e.getMessage(), e);
        }
    }

    public void removePaymentMethod(String paymentMethodId) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.detach();

            log.info("PaymentMethod detached: paymentMethodId={}", paymentMethodId);

        } catch (StripeException e) {
            log.error("Failed to detach PaymentMethod: {}", e.getMessage(), e);
            throw StripePaymentException.byCreationError(e.getMessage(), e);
        }
    }

    public PaymentResponse confirmPayment(String paymentIntentId, String paymentMethod, String returnUrl) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

             PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod(paymentMethod)
                    .setReturnUrl(returnUrl)
                    .build();

            PaymentIntent confirmed = paymentIntent.confirm(params);

            log.info("Payment confirmed: id={}, status={}", confirmed.getId(), confirmed.getStatus());
            return toPaymentResponse(confirmed);

        } catch (StripeException e) {
            log.error("Payment confirmation failed: {}", e.getMessage(), e);
            throw StripePaymentException.byConfirmedError(e.getMessage(), e);
        }
    }

    public PaymentResponse getPaymentStatus(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            log.info("Stripe payment status retrieved: id={}, status={}",
                    paymentIntent.getId(), paymentIntent.getStatus());

            return toPaymentResponse(paymentIntent);

        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe payment status: {}", e.getMessage(), e);
            throw StripePaymentException.byStatusError(e.getMessage(), e);
        }
    }

    // Helpers Methods
    private RefundCreateParams.Reason mapRefundReason(RefundReason reason) {
        return switch (reason) {
            case DUPLICATE -> RefundCreateParams.Reason.DUPLICATE;
            case FRAUDULENT -> RefundCreateParams.Reason.FRAUDULENT;
            case REQUESTED_BY_CUSTOMER -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }

    private PaymentResponse toPaymentResponse(PaymentIntent paymentIntent) {
        return PaymentResponse.builder()
                .id(paymentIntent.getId())
                .status(paymentIntent.getStatus())
                .amount(paymentIntent.getAmount())
                .currency(paymentIntent.getCurrency())
                .clientSecret(paymentIntent.getClientSecret())
                .description(paymentIntent.getDescription())
                .build();
    }


    private long convertToCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }

}

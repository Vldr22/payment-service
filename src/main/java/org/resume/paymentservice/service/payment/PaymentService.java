package org.resume.paymentservice.service.payment;

import com.stripe.model.PaymentIntent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.contants.BillingConstants;
import org.resume.paymentservice.exception.NotFoundException;
import org.resume.paymentservice.model.dto.data.PaymentCreationData;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.Subscription;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.PaymentStatus;
import org.resume.paymentservice.repository.PaymentRepository;
import org.resume.paymentservice.service.user.UserService;
import org.resume.paymentservice.utils.ErrorMessages;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserService userService;

    public Payment savePayment(PaymentCreationData data) {
        User user = userService.getUserById(data.getUserId());

        Payment payment = new Payment(
                data.getStripePaymentIntentId(),
                data.getAmount(),
                data.getCurrency(),
                PaymentStatus.PENDING,
                data.getDescription(),
                data.getClientSecret(),
                user,
                null
        );

        return paymentRepository.save(payment);
    }

    /**
     * Сохраняет платёж по подписке, возвращая существующий, если списание уже было записано.
     */
    public Payment saveBillingPayment(PaymentIntent paymentIntent, Subscription subscription) {
        return paymentRepository.findByStripePaymentIntentId(paymentIntent.getId())
                .orElseGet(() -> createBillingPayment(paymentIntent, subscription));
    }

    @Transactional
    public void updatePaymentStatus(String stripePaymentIntentId, PaymentStatus newStatus) {
        Payment payment = findByStripePaymentIntentId(stripePaymentIntentId);
        PaymentStatus oldStatus = payment.getStatus();

        if (oldStatus == newStatus) {
            log.debug("Payment status unchanged, skipping update: stripeId={}, status={}",
                    stripePaymentIntentId, newStatus);
            return;
        }

        if (!oldStatus.isPossibleTransitionTo(newStatus)) {
            log.warn("Payment status transition rejected: stripeId={}, {} -> {}",
                    stripePaymentIntentId, oldStatus, newStatus);
            return;
        }

        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        log.info("Payment status updated: stripeId={}, {} -> {}", stripePaymentIntentId, oldStatus, newStatus);
    }

    public void updateSavedCard(String stripePaymentIntentId, SavedCard savedCard) {
        Payment payment = findByStripePaymentIntentId(stripePaymentIntentId);
        payment.setSavedCard(savedCard);
        paymentRepository.save(payment);
        log.info("Payment saved card updated: stripeId={}, cardId={}",
                stripePaymentIntentId, savedCard.getId());
    }

    public Payment findByStripePaymentIntentId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> NotFoundException.paymentByStripeId(stripePaymentIntentId));
    }

    public Payment findByStripePaymentIntentIdAndUser(String stripePaymentIntentId, User user) {
        Payment payment = findByStripePaymentIntentId(stripePaymentIntentId);

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException(ErrorMessages.PAYMENT_ACCESS_DENIED);
        }
        return payment;
    }

    private Payment createBillingPayment(PaymentIntent paymentIntent, Subscription subscription) {
        Payment payment = new Payment(
                paymentIntent.getId(),
                subscription.getAmount(),
                subscription.getCurrency(),
                PaymentStatus.PENDING,
                String.format(BillingConstants.BILLING_PAYMENT_DESCRIPTION, subscription.getSubscriptionType().name()),
                null,
                subscription.getUser(),
                subscription.getSavedCard()
        );
        log.info("Billing payment saved: stripeId={}, subscriptionId={}",
                paymentIntent.getId(), subscription.getId());
        return paymentRepository.save(payment);
    }

}

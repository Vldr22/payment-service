package org.resume.paymentservice.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.paymentservice.exception.StripePaymentException;
import org.resume.paymentservice.model.dto.data.PaymentCreationData;
import org.resume.paymentservice.model.dto.request.ConfirmPaymentRequest;
import org.resume.paymentservice.model.dto.request.ConfirmWithSavedCardRequest;
import org.resume.paymentservice.model.dto.request.CreatePaymentRequest;
import org.resume.paymentservice.model.dto.request.RefundRequest;
import org.resume.paymentservice.model.dto.response.PaymentResponse;
import org.resume.paymentservice.model.dto.response.RefundResponse;
import org.resume.paymentservice.model.entity.Payment;
import org.resume.paymentservice.model.entity.Refund;
import org.resume.paymentservice.model.entity.SavedCard;
import org.resume.paymentservice.model.entity.User;
import org.resume.paymentservice.model.enums.PaymentStatus;
import org.resume.paymentservice.service.card.SavedCardService;
import org.resume.paymentservice.service.payment.PaymentService;
import org.resume.paymentservice.service.payment.RefundService;
import org.resume.paymentservice.service.payment.StripeService;
import org.resume.paymentservice.service.user.UserService;
import org.springframework.stereotype.Service;

import static org.resume.paymentservice.model.enums.PaymentStatus.mapStripeStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

    private final PaymentService paymentService;
    private final StripeService stripeService;
    private final UserService userService;
    private final RefundService refundService;
    private final SavedCardService savedCardService;

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        User user = userService.getCurrentUser();
        PaymentResponse stripeResponse = stripeService.createStripePayment(
                request, user.getStripeCustomerId()
        );

        PaymentCreationData data = buildPaymentCreationData(user.getId(), request, stripeResponse);
        paymentService.savePayment(data);

        log.info("Payment created: userId={}, stripePaymentIntentId={}",
                user.getId(), stripeResponse.getId());

        return stripeResponse;
    }

    public RefundResponse createRefund(String paymentIntentId, RefundRequest request) {
        User currentUser = userService.getCurrentUser();
        Payment payment = paymentService.findByStripePaymentIntentIdAndUser(paymentIntentId, currentUser);

        Refund refund = refundService.createRefundRequest(payment, currentUser, request.reason());

        log.info("Refund requested: refundId={}, paymentIntentId={}", refund.getId(), paymentIntentId);
        return toRefundResponse(refund);
    }

    public PaymentResponse confirmPayment(String paymentIntentId, ConfirmPaymentRequest request) {
        validatePaymentOwner(paymentIntentId);

        try {
            PaymentResponse response = stripeService.confirmPayment(
                    paymentIntentId, request.paymentMethod(), request.returnUrl()
            );

            PaymentStatus newStatus = PaymentStatus.mapStripeStatus(response.getStatus());
            paymentService.updatePaymentStatus(paymentIntentId, newStatus);

            log.info("Payment confirmed: paymentIntentId={}, status={}", paymentIntentId, newStatus);
            return response;

        } catch (StripePaymentException e) {
            paymentService.updatePaymentStatus(paymentIntentId, PaymentStatus.FAILED);
            log.warn("Payment confirmation failed, marked as FAILED: paymentIntentId={}", paymentIntentId);
            throw e;
        }
    }

    public PaymentResponse confirmPaymentWithSavedCard(String paymentIntentId, ConfirmWithSavedCardRequest request) {
        User user = userService.getCurrentUser();
        paymentService.findByStripePaymentIntentIdAndUser(paymentIntentId, user);

        SavedCard card = savedCardService.getCardByIdAndUser(request.savedCardId(), user);

        try {
            PaymentResponse response = stripeService.confirmPayment(
                    paymentIntentId, card.getStripePaymentMethodId(), request.returnUrl()
            );

            PaymentStatus newStatus = PaymentStatus.mapStripeStatus(response.getStatus());
            paymentService.updatePaymentStatus(paymentIntentId, newStatus);
            paymentService.updateSavedCard(paymentIntentId, card);

            log.info("Payment confirmed with saved card: paymentIntentId={}, cardId={}",
                    paymentIntentId, request.savedCardId());
            return response;

        } catch (StripePaymentException e) {
            paymentService.updatePaymentStatus(paymentIntentId, PaymentStatus.FAILED);
            log.warn("Payment confirmation failed: paymentIntentId={}", paymentIntentId);
            throw e;
        }
    }

    public PaymentResponse getPaymentStatus(String paymentIntentId) {
        validatePaymentOwner(paymentIntentId);

        PaymentResponse stripeResponse = stripeService.getPaymentStatus(paymentIntentId);
        PaymentStatus newStatus = mapStripeStatus(stripeResponse.getStatus());

        paymentService.updatePaymentStatus(paymentIntentId, newStatus);

        return stripeResponse;
    }

    /**
     * Проверяет, что платёж принадлежит текущему пользователю.
     */
    private void validatePaymentOwner(String paymentIntentId) {
        User currentUser = userService.getCurrentUser();
        paymentService.findByStripePaymentIntentIdAndUser(paymentIntentId, currentUser);
    }

    private PaymentCreationData buildPaymentCreationData(Long userId, CreatePaymentRequest request,
                                                         PaymentResponse stripeResponse) {
        return PaymentCreationData.builder()
                .userId(userId)
                .stripePaymentIntentId(stripeResponse.getId())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .clientSecret(stripeResponse.getClientSecret())
                .build();
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(
                refund.getPayment().getStripePaymentIntentId(),
                refund.getAmount(),
                refund.getPayment().getCurrency(),
                refund.getReason(),
                refund.getStatus()
        );
    }

}

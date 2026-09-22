package org.resume.paymentservice.repository;

import org.resume.paymentservice.model.entity.BillingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillingAttemptRepository extends JpaRepository<BillingAttempt, Long> {

    @Query("SELECT ba FROM BillingAttempt ba JOIN FETCH ba.subscription WHERE ba.subscription.id = :subscriptionId")
    List<BillingAttempt> findAllBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    Optional<BillingAttempt> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<BillingAttempt> findBySubscriptionIdAndBillingPeriod(Long subscriptionId, LocalDateTime billingPeriod);

}

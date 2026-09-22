package org.resume.paymentservice.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.resume.paymentservice.model.enums.BillingAttemptStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_attempts", indexes = {
        @Index(name = "idx_billing_attempts_subscription_id", columnList = "subscription_id"),
        @Index(name = "idx_billing_attempts_status", columnList = "status")
})
public class BillingAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BillingAttemptStatus status = BillingAttemptStatus.PENDING;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime billingPeriod;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column
    private LocalDateTime executedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public BillingAttempt(Subscription subscription, int attemptNumber, LocalDateTime billingPeriod) {
        this.subscription = subscription;
        this.attemptNumber = attemptNumber;
        this.billingPeriod = billingPeriod;
        this.scheduledAt = LocalDateTime.now();
    }
}

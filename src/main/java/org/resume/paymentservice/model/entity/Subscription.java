package org.resume.paymentservice.model.entity;

import jakarta.persistence.*;
import org.resume.paymentservice.utils.BigDecimalToLongConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.resume.paymentservice.model.enums.Currency;
import org.resume.paymentservice.model.enums.SubscriptionStatus;
import org.resume.paymentservice.model.enums.SubscriptionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_billing", columnList = "next_billing_date, subscription_status")
})
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private SubscriptionType subscriptionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.ACTIVE;

    @Column(nullable = false)
    @Convert(converter = BigDecimalToLongConverter.class)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private Integer intervalDays;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    private LocalDateTime nextBillingDate;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_card_id", nullable = false)
    private SavedCard savedCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_payment_id")
    private Payment lastPayment;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Subscription(User user, SavedCard savedCard, SubscriptionType type,
                        BigDecimal amount, Currency currency, int intervalDays) {
        this.user = user;
        this.savedCard = savedCard;
        this.subscriptionType = type;
        this.amount = amount;
        this.currency = currency;
        this.intervalDays = intervalDays;

        LocalDateTime now = LocalDateTime.now();
        this.startDate = now;
        this.endDate = now;
        this.nextBillingDate = now;
    }
}
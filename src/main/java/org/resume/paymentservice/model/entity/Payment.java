package org.resume.paymentservice.model.entity;

import jakarta.persistence.*;
import org.resume.paymentservice.utils.BigDecimalToLongConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.resume.paymentservice.model.enums.Currency;
import org.resume.paymentservice.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments", indexes = {
        @Index(name = "idx_stripe_payment_intent_id", columnList = "stripe_payment_intent_id"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, unique = true, nullable = false)
    private String stripePaymentIntentId;

    @Column(nullable = false)
    @Convert(converter = BigDecimalToLongConverter.class)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(length = 100)
    private String description;

    @Column(length = 200)
    private String clientSecret;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column()
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_card_id")
    private SavedCard savedCard;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Payment(String stripePaymentIntentId, BigDecimal amount, Currency currency,
                   PaymentStatus status, String description, String clientSecret,
                   User user, SavedCard cardToken) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.clientSecret = clientSecret;
        this.user = user;
        this.savedCard = cardToken;
    }

}
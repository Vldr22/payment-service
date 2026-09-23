package org.resume.paymentservice.model.entity;

import jakarta.persistence.*;
import org.resume.paymentservice.utils.BigDecimalToLongConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.resume.paymentservice.model.enums.RefundReason;
import org.resume.paymentservice.model.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, unique = true)
    private String stripeRefundId;

    @Column(nullable = false)
    @Convert(converter = BigDecimalToLongConverter.class)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private RefundReason reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Staff reviewedBy;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Refund(Payment payment, User user, RefundReason reason) {
        this.payment = payment;
        this.user = user;
        this.amount = payment.getAmount();
        this.reason = reason;
        this.status = RefundStatus.PENDING;
    }

}
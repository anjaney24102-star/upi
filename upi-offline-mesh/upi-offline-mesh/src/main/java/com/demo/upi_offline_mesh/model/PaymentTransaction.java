package com.demo.upi_offline_mesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class PaymentTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 20)
    private String status;

    protected PaymentTransaction() {}

    public PaymentTransaction(PaymentInstruction instruction) {
        this.transactionId = instruction.transactionId();
        this.sender = instruction.senderId();
        this.receiver = instruction.receiverId();
        this.amount = instruction.amount();
        this.timestamp = instruction.timestamp();
        this.status = "SETTLED";
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }
}
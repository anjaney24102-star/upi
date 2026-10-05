package com.demo.upi_offline_mesh.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentInstruction(
        String transactionId,
        String senderId,
        String receiverId,
        BigDecimal amount,
        Instant timestamp,
        Instant expiry,
        String nonce,
        String pin
) {
    public PaymentInstruction(
            String transactionId,
            String senderId,
            String receiverId,
            BigDecimal amount,
            Instant timestamp,
            Instant expiry,
            String nonce) {
        this(transactionId, senderId, receiverId, amount, timestamp, expiry, nonce, "");
    }
}
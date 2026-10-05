package com.demo.upi_offline_mesh.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentReceipt(
	String transactionId,
	BigDecimal amount,
	String sender,
	String receiver,
	Instant timestamp,
	String status
) {}
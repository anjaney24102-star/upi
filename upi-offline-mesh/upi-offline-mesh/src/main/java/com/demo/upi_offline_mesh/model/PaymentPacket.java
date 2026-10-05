package com.demo.upi_offline_mesh.model;

public record PaymentPacket(String transactionId, String iv, String ciphertext) {}
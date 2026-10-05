package com.demo.upi_offline_mesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class DemoAccount {

    @Id
    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "pin_hash", nullable = false, length = 160)
    private String pinHash;

    protected DemoAccount() {}

    public DemoAccount(String userId, BigDecimal balance, String pinHash) {
        this.userId = userId;
        this.balance = balance;
        this.pinHash = pinHash;
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
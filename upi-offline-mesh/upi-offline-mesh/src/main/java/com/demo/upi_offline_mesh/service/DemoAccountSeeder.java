package com.demo.upi_offline_mesh.service;

import com.demo.upi_offline_mesh.model.DemoAccount;
import com.demo.upi_offline_mesh.repository.DemoAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoAccountSeeder implements CommandLineRunner {

    private final DemoAccountRepository accountRepository;
    private final PinHasher pinHasher;

    public DemoAccountSeeder(DemoAccountRepository accountRepository, PinHasher pinHasher) {
        this.accountRepository = accountRepository;
        this.pinHasher = pinHasher;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            accountRepository.saveAll(List.of(
                    account("A", "5000.00"),
                    account("B", "3000.00"),
                    account("C", "2000.00")));
        }
    }

    private DemoAccount account(String userId, String balance) {
        return new DemoAccount(userId, new BigDecimal(balance), pinHasher.hash("1234"));
    }
}
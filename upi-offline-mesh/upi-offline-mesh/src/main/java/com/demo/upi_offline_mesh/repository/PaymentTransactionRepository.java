package com.demo.upi_offline_mesh.repository;

import com.demo.upi_offline_mesh.model.PaymentTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {
	List<PaymentTransaction> findAllByOrderByTimestampDesc();
}
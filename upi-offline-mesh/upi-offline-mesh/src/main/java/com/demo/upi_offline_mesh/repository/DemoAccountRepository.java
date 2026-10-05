package com.demo.upi_offline_mesh.repository;

import com.demo.upi_offline_mesh.model.DemoAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DemoAccountRepository extends JpaRepository<DemoAccount, String> {

    List<DemoAccount> findAllByOrderByUserIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from DemoAccount account where account.userId = :userId")
    Optional<DemoAccount> findLockedByUserId(@Param("userId") String userId);
}
package dev.bytebank.fraud.check;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface FraudCheckRepository extends JpaRepository<FraudCheck, Long> {

    Page<FraudCheck> findByOriginIban(String originIban, Pageable pageable);

    long countByOriginIbanAndCreatedAtGreaterThanEqual(String originIban, Instant since);
}

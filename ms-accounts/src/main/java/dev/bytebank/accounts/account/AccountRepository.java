package dev.bytebank.accounts.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIban(String iban);

    /**
     * Loads the account and locks its row until the transaction ends.
     * Prevents concurrent debits and credits from reading the same balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.iban = :iban")
    Optional<Account> findByIbanForUpdate(@Param("iban") String iban);

    List<Account> findByOwnerIdOrderByCreatedAt(Long ownerId);

    List<Account> findAllByOrderByCreatedAtAscIdAsc();

    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumber();
}

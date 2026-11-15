package dev.bytebank.accounts.account;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Cuenta bancaria. El saldo es BigDecimal con escala 2 (céntimos): nunca double, para evitar errores de coma flotante. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 34)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<AccountHolder> holders = new ArrayList<>();

    protected Account() {
    }

    public Account(String iban) {
        this.iban = iban;
        this.status = AccountStatus.ACTIVA;
        this.balance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVA;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

    public void changeStatus(AccountStatus status) {
        this.status = status;
    }

    public void addHolder(Long userId) {
        holders.add(new AccountHolder(this, userId));
    }

    public List<Long> getHolderIds() {
        return holders.stream().map(AccountHolder::getUserId).toList();
    }

    public boolean isHeldBy(Long userId) {
        return holders.stream().anyMatch(holder -> holder.getUserId().equals(userId));
    }

    public Long getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

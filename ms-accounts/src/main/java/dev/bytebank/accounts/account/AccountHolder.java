package dev.bytebank.accounts.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Titular de una cuenta. Los usuarios viven en auth_db, por eso user_id no tiene FK. */
@Entity
@Table(name = "account_holders")
public class AccountHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected AccountHolder() {
    }

    AccountHolder(Account account, Long userId) {
        this.account = account;
        this.userId = userId;
        this.addedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }
}

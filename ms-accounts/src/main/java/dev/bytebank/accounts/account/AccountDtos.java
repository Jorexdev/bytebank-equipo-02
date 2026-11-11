package dev.bytebank.accounts.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(String iban, AccountStatus status, BigDecimal balance, Long ownerId, Instant createdAt) {

        public static AccountResponse from(Account account) {
            return new AccountResponse(account.getIban(), account.getStatus(), account.getBalance(),
                    account.getOwnerId(), account.getCreatedAt());
        }
    }

    public record ChangeStatusRequest(@NotNull AccountStatus status) {
    }

    public record DepositRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                 @Size(max = 140) String concept) {
    }
}

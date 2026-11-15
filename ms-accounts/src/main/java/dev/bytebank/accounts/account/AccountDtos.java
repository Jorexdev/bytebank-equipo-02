package dev.bytebank.accounts.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(String iban, AccountStatus status, BigDecimal balance, List<Long> holderIds,
                                  Instant createdAt) {

        public static AccountResponse from(Account account) {
            return new AccountResponse(account.getIban(), account.getStatus(), account.getBalance(),
                    account.getHolderIds(), account.getCreatedAt());
        }
    }

    public record ChangeStatusRequest(@NotNull AccountStatus status) {
    }

    public record DepositRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                 @Size(max = 140) String concept) {
    }

    public record AddHolderRequest(@NotBlank @Email @Size(max = 180) String email) {
    }

    public record HolderResponse(Long userId, String email, String fullName) {
    }
}

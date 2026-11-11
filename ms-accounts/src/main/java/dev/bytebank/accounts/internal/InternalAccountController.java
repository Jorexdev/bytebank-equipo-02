package dev.bytebank.accounts.internal;

import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountService;
import dev.bytebank.accounts.account.AccountStatus;
import dev.bytebank.accounts.ledger.LedgerDtos.LedgerResult;
import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.ledger.LedgerService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones para otros servicios (requiere la clave interna). */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public InternalAccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    public record InternalAccountResponse(String iban, AccountStatus status, BigDecimal balance, Long ownerId) {
    }

    @GetMapping("/{iban}")
    public InternalAccountResponse get(@PathVariable String iban) {
        Account account = accountService.get(iban);
        return new InternalAccountResponse(account.getIban(), account.getStatus(), account.getBalance(),
                account.getOwnerId());
    }

    @PostMapping("/debit")
    public LedgerResult debit(@Valid @RequestBody MovementRequest request) {
        return ledgerService.debit(request);
    }

    @PostMapping("/credit")
    public LedgerResult credit(@Valid @RequestBody MovementRequest request) {
        return ledgerService.credit(request);
    }
}

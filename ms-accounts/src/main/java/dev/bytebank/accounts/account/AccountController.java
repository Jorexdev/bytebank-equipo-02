package dev.bytebank.accounts.account;

import dev.bytebank.accounts.account.AccountDtos.AccountResponse;
import dev.bytebank.accounts.account.AccountDtos.ChangeStatusRequest;
import dev.bytebank.accounts.account.AccountDtos.DepositRequest;
import dev.bytebank.accounts.movement.MovementResponse;
import dev.bytebank.accounts.movement.MovementService;
import dev.bytebank.common.security.CurrentUser;
import dev.bytebank.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final MovementService movementService;

    public AccountController(AccountService accountService, MovementService movementService) {
        this.accountService = accountService;
        this.movementService = movementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    public AccountResponse open() {
        return AccountResponse.from(accountService.open(CurrentUser.get()));
    }

    @GetMapping
    public List<AccountResponse> listForUser() {
        return accountService.listForUser(CurrentUser.get()).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{iban}")
    public AccountResponse get(@PathVariable String iban) {
        return AccountResponse.from(accountService.getForUser(iban, CurrentUser.get()));
    }

    /** Extracto paginado, del más reciente al más antiguo. */
    @GetMapping("/{iban}/movements")
    public PageResponse<MovementResponse> movements(
            @PathVariable String iban,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return movementService.statement(accountService.getForUser(iban, CurrentUser.get()), from, to, page, size);
    }

    /** Cambia el estado de una cuenta. Solo lo puede hacer un gestor. */
    @PatchMapping("/{iban}/status")
    @PreAuthorize("hasRole('GESTOR')")
    public AccountResponse changeStatus(@PathVariable String iban, @Valid @RequestBody ChangeStatusRequest request) {
        return AccountResponse.from(accountService.changeStatus(iban, request.status()));
    }

    /** Ingreso en ventanilla: solo lo puede hacer un gestor. */
    @PostMapping("/{iban}/deposits")
    @PreAuthorize("hasRole('GESTOR')")
    public AccountResponse deposit(@PathVariable String iban, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(accountService.deposit(iban, request.amount(), request.concept()));
    }
}

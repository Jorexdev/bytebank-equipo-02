package dev.bytebank.accounts.account;

import dev.bytebank.accounts.event.MovementPublisher;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final MovementPublisher publisher;

    public AccountService(AccountRepository accounts, MovementRepository movements, MovementPublisher publisher) {
        this.accounts = accounts;
        this.movements = movements;
        this.publisher = publisher;
    }

    @Transactional
    public Account open(AuthUser user) {
        Account account = accounts.save(new Account(Iban.generate(accounts.nextAccountNumber()), user.id()));
        log.info("Cuenta {} abierta por el usuario {}", account.getIban(), user.id());
        return account;
    }

    /**
     * Lists the accounts accessible to the authenticated user.
     * Gestors can access all accounts, while clients can only access their own.
     *
     * @param user the authenticated user
     * @return the list of accounts
     */
    @Transactional(readOnly = true)
    public List<Account> listForUser(AuthUser user) {
        if (user.isGestor()) {
            return accounts.findAllByOrderByCreatedAtAscIdAsc();
        }
        return accounts.findByOwnerIdOrderByCreatedAt(user.id());
    }

    /**
     * Retrieves an account by IBAN without checking user ownership.
     * Intended for internal operations between microservices.
     *
     * @param iban the IBAN of the account
     * @return the account
     * @throws ApiException if the account is not found
     */
    @Transactional(readOnly = true)
    public Account get(String iban) {
        return accounts.findByIban(Iban.normalize(iban))
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA",
                        "No existe la cuenta " + iban));
    }

    /**
     * Retrieves an account by IBAN after checking user ownership.
     * Gestors can access all accounts, while clients can only access their own.
     *
     * @param iban the IBAN of the account
     * @param user the authenticated user
     * @return the account
     * @throws ApiException if the account is not found or the user does not have
     *                      access
     */
    @Transactional(readOnly = true)
    public Account getForUser(String iban, AuthUser user) {
        Account account = get(iban);

        if (!user.isGestor() && !account.getOwnerId().equals(user.id())) {
            throw ApiException.forbidden(
                    "No puedes consultar una cuenta que no te pertenece");
        }

        return account;
    }

    @Transactional
    public Account changeStatus(String iban, AccountStatus status) {
        Account account = get(iban);
        account.changeStatus(status);
        log.info("Cuenta {} pasa a {}", account.getIban(), status);
        return account;
    }

    @Transactional
    public Account deposit(String iban, BigDecimal amount, String concept) {
        Account account = get(iban);
        if (!account.isActive()) {
            throw ApiException.conflict("CUENTA_NO_OPERATIVA", "La cuenta no está activa");
        }
        account.credit(amount);
        Movement movement = movements.save(new Movement(account, MovementType.INGRESO, amount,
                concept == null || concept.isBlank() ? "Ingreso" : concept, null));
        publisher.publish(movement, account);
        return account;
    }
}

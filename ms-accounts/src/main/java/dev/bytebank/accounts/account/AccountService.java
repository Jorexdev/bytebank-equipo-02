package dev.bytebank.accounts.account;

import dev.bytebank.accounts.account.AccountDtos.HolderResponse;
import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.UserInfo;
import dev.bytebank.accounts.event.MovementPublisher;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.accounts.movement.MovementType;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_HOLDERS = 4;

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final MovementPublisher publisher;
    private final AuthClient auth;

    public AccountService(AccountRepository accounts, MovementRepository movements, MovementPublisher publisher,
                          AuthClient auth) {
        this.accounts = accounts;
        this.movements = movements;
        this.publisher = publisher;
        this.auth = auth;
    }

    @Transactional
    public Account open(AuthUser user) {
        Account account = new Account(Iban.generate(accounts.nextAccountNumber()));
        account.addHolder(user.id());
        account = accounts.save(account);
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
        return accounts.findByHolderId(user.id());
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

        if (!user.isGestor() && !account.isHeldBy(user.id())) {
            throw ApiException.forbidden(
                    "No puedes consultar una cuenta que no te pertenece");
        }

        return account;
    }

    /**
     * Añade un titular a una cuenta. Bloquea la fila de la cuenta para que dos altas simultáneas no superen el
     * máximo de cuatro.
     */
    @Transactional
    public Account addHolder(String iban, String email, AuthUser user) {
        Account account = accounts.findByIbanForUpdate(Iban.normalize(iban))
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta " + iban));
        if (!account.isHeldBy(user.id())) {
            throw ApiException.forbidden("No puedes añadir titulares a una cuenta que no te pertenece");
        }
        UserInfo target = auth.findByEmail(email.trim());
        if (target.role() != Role.CLIENTE) {
            throw ApiException.badRequest("USUARIO_NO_CLIENTE", "Solo se puede añadir a un cliente");
        }
        if (account.isHeldBy(target.id())) {
            throw ApiException.conflict("TITULAR_YA_EXISTE", "Ese cliente ya es titular de la cuenta");
        }
        if (account.getHolderIds().size() >= MAX_HOLDERS) {
            throw ApiException.conflict("MAXIMO_TITULARES_ALCANZADO", "La cuenta ya tiene cuatro titulares");
        }
        account.addHolder(target.id());
        log.info("Titular {} añadido a la cuenta {}", target.id(), account.getIban());
        return account;
    }

    /** Datos de los titulares de una cuenta, resueltos contra ms-auth. */
    public List<HolderResponse> holders(String iban, AuthUser user) {
        Account account = getForUser(iban, user);
        return account.getHolderIds().stream()
                .map(auth::findById)
                .map(target -> new HolderResponse(target.id(), target.email(), target.fullName()))
                .toList();
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

        Account account = accounts.findByIbanForUpdate(Iban.normalize(iban))
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta " + iban));
        
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

package dev.bytebank.accounts.ledger;

import dev.bytebank.accounts.account.Account;
import dev.bytebank.accounts.account.AccountRepository;
import dev.bytebank.accounts.event.MovementPublisher;
import dev.bytebank.accounts.ledger.LedgerDtos.LedgerResult;
import dev.bytebank.accounts.ledger.LedgerDtos.MovementRequest;
import dev.bytebank.accounts.movement.Movement;
import dev.bytebank.accounts.movement.MovementRepository;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cargos y abonos en cuenta. Cada operación es transaccional. */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final MovementPublisher publisher;

    public LedgerService(AccountRepository accounts, MovementRepository movements, MovementPublisher publisher) {
        this.accounts = accounts;
        this.movements = movements;
        this.publisher = publisher;
    }

    @Transactional
    public LedgerResult debit(MovementRequest request) {
        Account account = find(request.iban());
        if (!account.isActive()) {
            throw ApiException.conflict("CUENTA_NO_OPERATIVA", "La cuenta " + account.getIban() + " no está activa");
        }
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw ApiException.conflict("FONDOS_INSUFICIENTES", "Saldo insuficiente");
        }
        account.debit(request.amount());
        accounts.save(account);
        Movement movement = movements.save(new Movement(account, request.type(), request.amount().negate(),
                conceptOrDefault(request.concept()), request.reference()));
        publisher.publish(movement, account);
        log.info("Cargo {} de {} en {}", request.reference(), request.amount(), account.getIban());
        return new LedgerResult(request.reference(), account.getBalance());
    }

    @Transactional
    public LedgerResult credit(MovementRequest request) {
        Account account = find(request.iban());
        if (!account.isActive()) {
            throw ApiException.conflict("CUENTA_NO_OPERATIVA", "La cuenta " + account.getIban() + " no está activa");
        }
        account.credit(request.amount());
        accounts.save(account);
        Movement movement = movements.save(new Movement(account, request.type(), request.amount(),
                conceptOrDefault(request.concept()), request.reference()));
        publisher.publish(movement, account);
        log.info("Abono {} de {} en {}", request.reference(), request.amount(), account.getIban());
        return new LedgerResult(request.reference(), account.getBalance());
    }

    private Account find(String iban) {
        return accounts.findByIban(Iban.normalize(iban))
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta " + iban));
    }

    private static String conceptOrDefault(String concept) {
        return concept == null || concept.isBlank() ? "Transferencia" : concept;
    }
}

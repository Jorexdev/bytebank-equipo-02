package dev.bytebank.cards.card;

import dev.bytebank.cards.client.AccountsClient;
import dev.bytebank.cards.payment.CardPayment;
import dev.bytebank.cards.payment.CardPaymentRepository;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.http.RemoteUnavailableException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> BUSINESS_REJECTIONS = Set.of("FONDOS_INSUFICIENTES", "CUENTA_NO_OPERATIVA");

    private final CardRepository cards;
    private final CardPaymentRepository payments;
    private final AccountsClient accountsClient;
    private final Clock clock;
    private final ZoneId zone;
    private final BigDecimal defaultDailyLimit;

    public CardService(CardRepository cards, CardPaymentRepository payments, AccountsClient accountsClient, Clock clock,
                       @Value("${bytebank.zone:Europe/Madrid}") String zone,
                       @Value("${bytebank.cards.default-daily-limit}") BigDecimal defaultDailyLimit) {
        this.cards = cards;
        this.payments = payments;
        this.accountsClient = accountsClient;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.defaultDailyLimit = defaultDailyLimit;
    }

    /** Emite una tarjeta de débito para una cuenta del usuario. Ver docs/contratos/ms-cards.md. */
    public Card issue(String iban, AuthUser user) {
        AccountsClient.AccountInfo account = accountsClient.getAccount(Iban.normalize(iban));
        if (!account.isHeldBy(user.id())) {
            throw ApiException.notFound("CUENTA_NO_ENCONTRADA", "No existe la cuenta " + iban);
        }
        if (!account.isActive()) {
            throw ApiException.conflict("CUENTA_NO_OPERATIVA", "La cuenta no está activa");
        }
        String last4 = String.format("%04d", RANDOM.nextInt(10_000));
        return cards.save(new Card(account.iban(), user.id(), last4, defaultDailyLimit));
    }

    public List<Card> listOwn(AuthUser user) {
        return cards.findByUserIdOrderByCreatedAt(user.id());
    }

    public Card get(Long id, AuthUser user) {
        Card card = cards.findById(id).orElseThrow(() -> notFound(id));
        if (!card.getUserId().equals(user.id())) {
            throw notFound(id);
        }
        return card;
    }

    public Card changeStatus(Long id, CardStatus status, AuthUser user) {
        Card card = get(id, user);
        card.changeStatus(status);
        return cards.save(card);
    }

    /** Cobra un pago con la tarjeta. Ver docs/contratos/ms-cards.md. */
    @Transactional
    public CardPayment pay(Long id, BigDecimal amount, String merchant, AuthUser user) {
        Card card = cards.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!card.getUserId().equals(user.id())) {
            throw notFound(id);
        }

        String reference = "CARD-" + UUID.randomUUID();
        CardPayment payment = new CardPayment(reference, card.getId(), amount, merchant, clock.instant());

        if (!card.isActive()) {
            payment.reject("TARJETA_BLOQUEADA");
            return payments.save(payment);
        }

        Instant todayStart = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        BigDecimal spent = payments.sumApprovedAmountSince(card.getId(), todayStart);
        if (spent.add(amount).compareTo(card.getDailyLimit()) > 0) {
            payment.reject("LIMITE_DIARIO_SUPERADO");
            return payments.save(payment);
        }

        try {
            accountsClient.debit(reference, card.getAccountIban(), amount, "Pago en " + merchant);
        } catch (RemoteUnavailableException e) {
            throw e;
        } catch (ApiException e) {
            if (!BUSINESS_REJECTIONS.contains(e.getCode())) {
                throw e;
            }
            payment.reject(e.getCode());
            return payments.save(payment);
        }

        payment.approve();
        try {
            return payments.saveAndFlush(payment);
        } catch (RuntimeException e) {
            try {
                accountsClient.credit(reference + "-REV", card.getAccountIban(), amount,
                        "Reverso del pago " + reference);
            } catch (RuntimeException reversalFailure) {
                log.error("No se pudo revertir el pago {} después de fallar su guardado", reference, reversalFailure);
            }
            throw e;
        }
    }

    public List<CardPayment> payments(Long id, AuthUser user) {
        Card card = get(id, user);
        return payments.findByCardIdOrderByCreatedAtDescIdDesc(card.getId());
    }

    private static ApiException notFound(Long id) {
        return ApiException.notFound("TARJETA_NO_ENCONTRADA", "No existe la tarjeta " + id);
    }
}

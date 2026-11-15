package dev.bytebank.cards.card;

import dev.bytebank.cards.client.AccountsClient;
import dev.bytebank.cards.payment.CardPayment;
import dev.bytebank.cards.payment.CardPaymentRepository;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

@Service
public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();

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
    public CardPayment pay(Long id, BigDecimal amount, String merchant, AuthUser user) {
        // TODO pendiente de implementar
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "NO_IMPLEMENTADO", "Los pagos con tarjeta aún no están disponibles");
    }

    public List<CardPayment> payments(Long id, AuthUser user) {
        Card card = get(id, user);
        return payments.findByCardIdOrderByCreatedAtDescIdDesc(card.getId());
    }

    private static ApiException notFound(Long id) {
        return ApiException.notFound("TARJETA_NO_ENCONTRADA", "No existe la tarjeta " + id);
    }
}

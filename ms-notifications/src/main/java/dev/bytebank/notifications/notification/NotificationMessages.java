package dev.bytebank.notifications.notification;

import dev.bytebank.notifications.event.MovementEvent;

import java.math.BigDecimal;
import java.util.Locale;

/** Texto del aviso que ve el cliente. */
final class NotificationMessages {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private NotificationMessages() {
    }

    static String of(MovementEvent event) {
        String amount = money(event.amount().abs());
        String account = "tu cuenta ···" + event.iban().substring(event.iban().length() - 4);
        String concept = event.concept() == null || event.concept().isBlank() ? "" : " (" + event.concept() + ")";
        String text = switch (event.type()) {
            case "INGRESO" -> "Ingreso de " + amount + " en " + account + concept;
            case "TRANSFERENCIA_EMITIDA" -> "Has enviado " + amount + " desde " + account + concept;
            case "TRANSFERENCIA_RECIBIDA" -> "Has recibido " + amount + " en " + account + concept;
            case "PAGO_TARJETA" -> "Pago con tarjeta de " + amount + " en " + account + concept;
            default -> "Movimiento de " + money(event.amount()) + " en " + account + concept;
        };
        return text + ". Saldo: " + money(event.balanceAfter());
    }

    private static String money(BigDecimal value) {
        return String.format(ES, "%,.2f €", value);
    }
}

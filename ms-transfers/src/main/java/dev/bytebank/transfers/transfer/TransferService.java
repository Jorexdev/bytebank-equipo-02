package dev.bytebank.transfers.transfer;

import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.web.PageResponse;
import dev.bytebank.transfers.client.AccountsClient;
import dev.bytebank.transfers.transfer.TransferDtos.CreateTransferRequest;
import dev.bytebank.transfers.transfer.TransferDtos.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransferRepository transfers;
    private final AccountsClient accountsClient;

    public TransferService(TransferRepository transfers, AccountsClient accountsClient) {
        this.transfers = transfers;
        this.accountsClient = accountsClient;
    }

    public Transfer create(CreateTransferRequest request, AuthUser user) {
        String origin = Iban.normalize(request.originIban());
        String destination = Iban.normalize(request.destinationIban());
        BigDecimal amount = request.amount();

        if (!Iban.isValid(destination)) {
            throw ApiException.badRequest("IBAN_NO_VALIDO", "El IBAN de destino no es válido");
        }
        if (origin.equals(destination)) {
            throw ApiException.badRequest("MISMA_CUENTA", "La cuenta de origen y la de destino son la misma");
        }
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("IMPORTE_NO_VALIDO", "El importe debe ser mayor que cero");
        }
        AccountsClient.AccountInfo originAccount = accountsClient.getAccount(origin);

        if (!user.id().equals(originAccount.ownerId())) {
            throw ApiException.forbidden("No puedes hacer transferencias desde cuentas que no te pertenecen");
        }

        // TODO validar con ms-fraud antes de ejecutar (ver docs/fraude.md)

        Transfer transfer = new Transfer(origin, destination, amount, request.concept(), user.id());

        if (isBlockedDestination(destination)) {
            transfer.reject("CUENTA_NO_OPERATIVA");
            log.info("Transferencia rechazada antes de ejecutarse: destino no operativo");            return transfers.save(transfer);
        }

        transfer = transfers.save(transfer);
        try {
            accountsClient.debit(transfer.ledgerReference(), origin, amount, request.concept());
        } catch (ApiException e) {
            transfer.reject(e.getCode());
            log.info("Transferencia {} rechazada: {}", transfer.getId(), e.getCode());
            return transfers.save(transfer);
        }

        if (Iban.isOwnBank(destination)) {
            try {
                accountsClient.credit(transfer.ledgerReference(), destination, amount, request.concept());
            } catch (ApiException e) {
                log.warn("No se pudo abonar la transferencia {} en {}: {}", transfer.getId(), destination, e.getCode());
                reverse(transfer, origin, amount);
                transfer.reject(e.getCode());
                return transfers.save(transfer);
            }
        }
        transfer.complete();
        log.info("Transferencia {} completada", transfer.getId());
        return transfers.save(transfer);
    }

    /**
     * Checks whether the destination is an account of this bank that cannot receive money.
     * Checked before debiting the origin so no money moves and no reversal is needed.
     */
    private boolean isBlockedDestination(String destination) {
        return Iban.isOwnBank(destination) && !accountsClient.getAccount(destination).isActive();
    }

    /**
     * Returns the money to the origin when crediting the destination has failed.
     * The reversal is recorded with reference {@code TRF-<id>-REV} so it is traceable in the statement.
     */
    private void reverse(Transfer transfer, String origin, BigDecimal amount) {
        try {
            accountsClient.credit(transfer.ledgerReference() + "-REV", origin, amount,
                    "Reverso de la transferencia " + transfer.getId());
        } catch (ApiException e) {
            // shortcut: if the reversal also fails it is only logged, for manual review
            log.error("No se pudo revertir el cargo de la transferencia {}", transfer.getId());
        }
    }

    public Transfer get(Long id, AuthUser user) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> ApiException.notFound("TRANSFERENCIA_NO_ENCONTRADA", "No existe la transferencia " + id));
        if (!transfer.getCreatedBy().equals(user.id())) {
            throw ApiException.forbidden("No puedes ver esta transferencia");
        }
        return transfer;
    }

    public PageResponse<TransferResponse> list(int page, int size, AuthUser user) {
        var result = transfers.findByCreatedBy(user.id(), PageRequest.of(page, size, NEWEST_FIRST));
        return PageResponse.of(result, TransferResponse::from);
    }
}

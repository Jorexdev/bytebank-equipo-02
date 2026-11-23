package dev.bytebank.transfers.transfer;

public enum TransferStatus {
    /** Pendiente de que un gestor la apruebe o la rechace (CAM-02 o veredicto REVISAR). */
    PENDIENTE_REVISION,
    COMPLETADA,
    RECHAZADA
}

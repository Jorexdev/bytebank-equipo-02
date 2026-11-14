package dev.bytebank.transfers.transfer;

import dev.bytebank.common.security.CurrentUser;
import dev.bytebank.common.web.PageResponse;
import dev.bytebank.transfers.transfer.TransferDtos.CreateTransferRequest;
import dev.bytebank.transfers.transfer.TransferDtos.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENTE')")
    public TransferResponse create(@Valid @RequestBody CreateTransferRequest request) {
        return TransferResponse.from(transferService.create(request, CurrentUser.get()));
    }

    @GetMapping
    public PageResponse<TransferResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return transferService.list(page, size, CurrentUser.get());
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return TransferResponse.from(transferService.get(id, CurrentUser.get()));
    }

    /** Approves a pending transfer. Only gestors can do it. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('GESTOR')")
    public TransferResponse approve(@PathVariable Long id) {
        return TransferResponse.from(transferService.approve(id));
    }

    /** Rejects a pending transfer. Only gestors can do it. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('GESTOR')")
    public TransferResponse reject(@PathVariable Long id) {
        return TransferResponse.from(transferService.reject(id));
    }
}

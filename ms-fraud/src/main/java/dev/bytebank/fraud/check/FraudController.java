package dev.bytebank.fraud.check;

import dev.bytebank.common.web.PageResponse;
import dev.bytebank.fraud.check.FraudDtos.CheckRequest;
import dev.bytebank.fraud.check.FraudDtos.CheckResponse;
import dev.bytebank.fraud.check.FraudDtos.CheckView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class FraudController {

    private final FraudService fraudService;

    public FraudController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    /** Lo llama ms-transfers antes de ejecutar cada transferencia. */
    @PostMapping("/internal/fraud/check")
    public CheckResponse check(@Valid @RequestBody CheckRequest request) {
        return fraudService.check(request);
    }

    /** Lists the stored checks. Only gestors can consult them. */
    @GetMapping("/api/fraud/checks")
    @PreAuthorize("hasRole('GESTOR')")
    public PageResponse<CheckView> list(@RequestParam(required = false) String iban,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fraudService.list(iban, page, size);
    }
}

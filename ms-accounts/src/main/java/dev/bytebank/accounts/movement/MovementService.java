package dev.bytebank.accounts.movement;

import dev.bytebank.accounts.account.Account;
import dev.bytebank.common.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MovementService {

    private static final Instant MIN = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("3000-01-01T00:00:00Z");

    private final MovementRepository movements;

    public MovementService(MovementRepository movements) {
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> statement(Account account, LocalDate from, LocalDate to, int page, int size) {
        Instant start = from == null ? MIN : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        // 'to' es exclusivo, como en el resto de rangos de la API
        Instant end = to == null ? MAX : to.atStartOfDay(ZoneOffset.UTC).toInstant();

        long total = movements.countInRange(account.getId(), start, end);
        int offset = page * size;
        List<MovementResponse> content = movements.findSlice(account.getId(), start, end, size, offset)
                .stream().map(MovementResponse::from).toList();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}

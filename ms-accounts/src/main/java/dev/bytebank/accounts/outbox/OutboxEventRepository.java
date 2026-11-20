package dev.bytebank.accounts.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Pendientes más antiguos primero, bloqueados para esta transacción. SKIP LOCKED salta los que ya tiene
     * otra instancia, de modo que dos relays nunca publican el mismo evento a la vez.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> findPendingForUpdate(@Param("limit") int limit);

    /**
     * Borra los eventos publicados antes de esa fecha. Los pendientes no se borran
     * nunca.
     */
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

}

package dev.bytebank.cards.card;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CardRepositoryContractTest {

    @Test
    void exposesLockedCardLookup() throws Exception {
        var method = CardRepository.class.getMethod("findByIdForUpdate", Long.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }
}

package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountHoldersIntegrationTest extends IntegrationTest {

    @Autowired
    AccountService service;
    @Autowired
    AccountRepository accounts;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");
    }

    @Test
    void openAddsCreatorAsHolder() {
        Account account = service.open(client(2L));

        assertThat(account.getHolderIds()).containsExactly(2L);
        assertThat(jdbc.queryForObject(
                "select count(*) from account_holders where account_id = ?", Integer.class, account.getId()))
                .isEqualTo(1);
    }

    @Test
    void everyHolderCanReadTheAccount() {
        Account account = sharedAccount(2L, 3L);

        assertThat(service.getForUser(account.getIban(), client(3L)).getIban()).isEqualTo(account.getIban());
        assertThatThrownBy(() -> service.getForUser(account.getIban(), client(99L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void sharedAccountAppearsInEveryHoldersList() {
        Account account = sharedAccount(2L, 3L);

        assertThat(service.listForUser(client(2L))).extracting(Account::getIban)
                .containsExactly(account.getIban());
        assertThat(service.listForUser(client(3L))).extracting(Account::getIban)
                .containsExactly(account.getIban());
    }

    private Account sharedAccount(Long... holderIds) {
        Account account = new Account(Iban.generate(accounts.nextAccountNumber()));
        for (Long holderId : holderIds) {
            account.addHolder(holderId);
        }
        return accounts.save(account);
    }

    private static AuthUser client(Long id) {
        return new AuthUser(id, id + "@test.com", Role.CLIENTE);
    }
}

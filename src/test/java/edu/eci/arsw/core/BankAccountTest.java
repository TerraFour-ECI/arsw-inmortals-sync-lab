package edu.eci.arsw.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankAccountTest {

    @Test
    void shouldCreateAccountWithIdAndInitialBalance() {
        BankAccount account = new BankAccount(1L, 1000L);
        assertEquals(1L, account.id());
        assertEquals(1000L, account.balance());
    }

    @Test
    void shouldReturnLock() {
        BankAccount account = new BankAccount(1L, 500L);
        assertNotNull(account.lock());
    }

    @Test
    void shouldDepositAmount() {
        BankAccount account = new BankAccount(1L, 1000L);
        account.depositInternal(500L);
        assertEquals(1500L, account.balance());
    }

    @Test
    void shouldWithdrawAmount() {
        BankAccount account = new BankAccount(1L, 1000L);
        account.withdrawInternal(300L);
        assertEquals(700L, account.balance());
    }

    @Test
    void shouldHandleZeroInitialBalance() {
        BankAccount account = new BankAccount(99L, 0L);
        assertEquals(99L, account.id());
        assertEquals(0L, account.balance());
    }
}

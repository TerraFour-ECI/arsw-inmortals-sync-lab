package edu.eci.arsw.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class TransferServiceTest {

    private BankAccount accountA;
    private BankAccount accountB;

    @BeforeEach
    void setUp() {
        accountA = new BankAccount(1L, 1000L);
        accountB = new BankAccount(2L, 500L);
    }

    @Test
    void transferOrderedShouldMoveMoneyBetweenAccounts() {
        TransferService.transferOrdered(accountA, accountB, 200L);
        assertEquals(800L, accountA.balance());
        assertEquals(700L, accountB.balance());
    }

    @Test
    void transferOrderedShouldWorkWithReversedIds() {
        BankAccount from = new BankAccount(10L, 1000L);
        BankAccount to = new BankAccount(5L, 500L);
        TransferService.transferOrdered(from, to, 300L);
        assertEquals(700L, from.balance());
        assertEquals(800L, to.balance());
    }

    @Test
    void transferOrderedShouldThrowOnInsufficientFunds() {
        assertThrows(IllegalArgumentException.class, () ->
            TransferService.transferOrdered(accountA, accountB, 2000L)
        );
    }

    @Test
    void transferOrderedShouldThrowOnNullFrom() {
        assertThrows(NullPointerException.class, () ->
            TransferService.transferOrdered(null, accountB, 100L)
        );
    }

    @Test
    void transferOrderedShouldThrowOnNullTo() {
        assertThrows(NullPointerException.class, () ->
            TransferService.transferOrdered(accountA, null, 100L)
        );
    }

    @Test
    void transferTryLockShouldMoveMoneyBetweenAccounts() throws InterruptedException {
        TransferService.transferTryLock(accountA, accountB, 150L, Duration.ofSeconds(1));
        assertEquals(850L, accountA.balance());
        assertEquals(650L, accountB.balance());
    }

    @Test
    void transferTryLockShouldThrowOnInsufficientFunds() {
        assertThrows(IllegalArgumentException.class, () ->
            TransferService.transferTryLock(accountA, accountB, 5000L, Duration.ofSeconds(1))
        );
    }

    @Test
    void transferTryLockShouldThrowOnNullFrom() {
        assertThrows(NullPointerException.class, () ->
            TransferService.transferTryLock(null, accountB, 100L, Duration.ofSeconds(1))
        );
    }

    @Test
    void transferTryLockShouldThrowOnNullTo() {
        assertThrows(NullPointerException.class, () ->
            TransferService.transferTryLock(accountA, null, 100L, Duration.ofSeconds(1))
        );
    }

    @Test
    void transferNaiveShouldMoveMoneyBetweenAccounts() {
        TransferService.transferNaive(accountA, accountB, 100L);
        assertEquals(900L, accountA.balance());
        assertEquals(600L, accountB.balance());
    }

    @Test
    void transferNaiveShouldThrowOnInsufficientFunds() {
        assertThrows(IllegalArgumentException.class, () ->
            TransferService.transferNaive(accountA, accountB, 3000L)
        );
    }

    @Test
    void transferNaiveShouldThrowOnNullAccounts() {
        assertThrows(NullPointerException.class, () ->
            TransferService.transferNaive(null, accountB, 100L)
        );
        assertThrows(NullPointerException.class, () ->
            TransferService.transferNaive(accountA, null, 100L)
        );
    }
}

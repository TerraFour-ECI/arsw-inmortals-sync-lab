# Assignment — Lab_Inmortals-Sync_Java21 (ARSW Lab #3)

**Objective:** Practice minimal synchronization, avoid deadlocks and cooperative suspension in an "immortals"-type thread system.

## Suggested steps
1) Run with `-Dfight=naive` and record issues (negative health, inconsistencies).
2) Fix with **total order** (or `tryLock(timeout)`) and compare results.
3) Implement stable **Pause/Resume** with `PauseController`.
4) Implement orderly **Stop** (interrupts + executor shutdown).
5) Make `ScoreBoard` **thread-safe** (counters/statistics).
6) (Optional) Dining Philosophers: deadlock + solution.

## Deliverables
- Java 21 code, `ANSWERS.txt` with analysis and evidence (thread dumps/screenshots).

## Reading
Goetz et al., *Java Concurrency in Practice*, pages **1–4** and **15–21**.

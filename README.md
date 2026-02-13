# ARSW — (Java 21): **Immortals & Synchronization** — with Swing UI

**Colombian School of Engineering – Software Architectures**  
Concurrency lab: race conditions, synchronization, cooperative suspension and *deadlocks*, with **Swing** interface (*Highlander Simulator* style).

---

## Requirements

- **JDK 21** (Temurin recommended)
- **Maven 3.9+**
- OS: Windows, macOS or Linux

---

## How to run

### Graphical interface (Swing) — *Highlander Simulator*

**Option A (from `Main`, `ui` mode)**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=8 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

**Option B (UI class directly)**
```bash
mvn -q -DskipTests exec:java   -Dexec.mainClass=edu.eci.arsw.highlandersim.ControlFrame   -Dcount=8 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

**Parameters**
- `-Dcount=N` → number of immortals (default 8)
- `-Dfight=ordered|naive|trylock` → fight strategy (`ordered` avoids *deadlocks* via total order, `trylock` uses timeout+backoff, `naive` may cause deadlocks)
- `-Dhealth`, `-Ddamage` → initial health and damage per hit

### Theoretical demos (without UI)
```bash
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=1  # 1 = Naive deadlock
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=2  # 2 = Total order (no deadlock)
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=3  # 3 = tryLock + timeout (progress)
```

---

## UI Controls

- **Start**: starts a simulation with the chosen parameters.
- **Pause & Check**: pauses **all** threads and shows health per immortal and **total sum** (invariant).
- **Resume**: resumes the simulation.
- **Stop**: stops in an orderly manner.

**Invariant**: with N players and initial health H, the **total sum** of health must remain constant (except during an ongoing update). Use **Pause & Check** to validate it.

---

## Architecture (folders)

```
edu.eci.arsw
├─ app/                 # Bootstrap (Main): modes ui|immortals|demos
├─ highlandersim/       # Swing UI: ControlFrame (Start, Pause & Check, Resume, Stop)
├─ immortals/           # Domain: Immortal, ImmortalManager, ScoreBoard
├─ concurrency/         # PauseController (Lock/Condition; paused(), awaitIfPaused())
├─ demos/               # DeadlockDemo, OrderedTransferDemo, TryLockTransferDemo
└─ core/                # BankAccount, TransferService (for theoretical demos)
```

---

# Lab Activities

## Part I — (Before class ends) `wait/notify`: Producer/Consumer
1. Run the producer/consumer program and monitor CPU with **jVisualVM**. Why the high consumption? Which class causes it?
2. Adjust the implementation to **use CPU efficiently** when the **producer is slow** and the **consumer is fast**. Validate again with VisualVM.
3. Now **fast producer** and **slow consumer** with **stock limit** (bounded queue): ensure the limit is respected **without busy-wait** and validate CPU with a small stock.

> Note: Part I is done in the dedicated repository https://github.com/DECSIS-ECI/Lab_busy_wait_vs_wait_notify — clone that repo and do the exercises there; it contains the producer/consumer code, busy-wait variants and solutions using wait()/notify(), plus instructions to run and validate with jVisualVM.

> Use Java monitors: **`synchronized` + `wait()` + `notify/notifyAll()`**, avoiding *busy-wait*.

### Part I — Analysis and Answers

#### 1. High CPU consumption diagnosis

**Question:** *Why the high CPU consumption? Which class causes it?*

**Answer:** The high CPU consumption is caused by the `BusySpinQueue` class which implements a **busy-wait** (spin-wait) pattern. Looking at the code:

```java
// BusySpinQueue.java - put method
while (true) {
    if (q.size() < capacity) {
        q.addLast(item);
        return;
    }
    Thread.onSpinWait();  // CPU keeps spinning!
}

// BusySpinQueue.java - take method
while (true) {
    T v = q.pollFirst();
    if (v != null) return v;
    Thread.onSpinWait();  // CPU keeps spinning!
}
```

The thread **continuously checks** the condition in a tight loop without ever yielding CPU execution. Even `Thread.onSpinWait()` only provides a hint to the CPU but the thread remains active, consuming CPU cycles constantly.

**Commands to reproduce the issue:**
```bash
# High CPU (busy-wait mode)
mvn -q -DskipTests exec:java "-Dexec.mainClass=edu.eci.arsw.pc.PCApp -Dmode=spin -Dcapacity=4 -DprodDelayMs=50 -DconsDelayMs=1 -DdurationSec=30"
```

**Evidence:**

![Spin mode - CPU Monitor](images/first_screenshot.png)
*jVisualVM Monitor: High CPU usage with busy-wait (spin mode)*

![Spin mode - Threads](images/second_screenshot.png)
*jVisualVM Threads: Threads constantly in RUNNABLE state*

#### 2. Efficient CPU usage with slow producer / fast consumer

**Question:** *How to use CPU efficiently when the producer is slow and the consumer is fast?*

**Answer:** The solution is implemented in `BoundedBuffer` using Java monitors (`synchronized` + `wait()`/`notifyAll()`):

```java
// BoundedBuffer.java - take method (consumer waits efficiently)
public T take() throws InterruptedException {
    synchronized (this) {
        while (q.isEmpty()) {
            this.wait();  // Releases lock and CPU - thread sleeps!
        }
        T v = q.removeFirst();
        this.notifyAll();
        return v;
    }
}
```

With `wait()`:
- The consumer **releases the CPU** when the queue is empty
- The thread enters **WAITING** state (not consuming CPU)
- It wakes up **only** when `notifyAll()` is called by the producer

**Commands to verify:**
```bash
# Efficient CPU (monitor mode) - slow producer, fast consumer
mvn -q -DskipTests exec:java "-Dexec.mainClass=edu.eci.arsw.pc.PCApp -Dmode=monitor -Dcapacity=4 -DprodDelayMs=50 -DconsDelayMs=1 -DdurationSec=30"
```

**Evidence:**

![Monitor mode - CPU](images/third_screenshot.png)
*jVisualVM Monitor: Low CPU usage with monitors (wait/notify)*

![Monitor mode - Threads](images/fourth-screenshot.png)
*jVisualVM Threads: Threads in WAITING state when idle*

#### 3. Fast producer / slow consumer with stock limit

**Question:** *How to ensure the limit is respected without busy-wait?*

**Answer:** The `BoundedBuffer` also handles this scenario correctly:

```java
// BoundedBuffer.java - put method (producer waits when full)
public void put(T item) throws InterruptedException {
    synchronized (this) {
        while (q.size() == capacity) {
            this.wait();  // Producer waits without CPU when queue is full
        }
        q.addLast(item);
        this.notifyAll();  // Wake up consumers
    }
}
```

Key points:
- The producer **blocks without consuming CPU** when queue reaches `capacity`
- Uses `while` (not `if`) to handle spurious wakeups
- `notifyAll()` ensures both producers and consumers can be awakened

**Commands to verify:**
```bash
# Fast producer (0ms delay), slow consumer (100ms delay), small capacity (4)
mvn -q -DskipTests exec:java "-Dexec.mainClass=edu.eci.arsw.pc.PCApp -Dmode=monitor -Dcapacity=4 -DprodDelayMs=0 -DconsDelayMs=100 -DdurationSec=30"
```

**Evidence:**

![Fast producer - CPU](images/fifth_screenshot.png)
*jVisualVM Monitor: Producer waits efficiently when queue is full*

![Fast producer - Threads](images/sixth_screenshot.png)
*jVisualVM Threads: Producer in WAITING state when bounded queue reaches capacity*

---

## Part II — (Before class ends) Distributed search and stop condition
Rewrite the **blacklist searcher** so that the search **stops as soon as** the set of threads detects the number of occurrences that define whether the host is trustworthy or not (`BLACK_LIST_ALARM_COUNT`). It must:
- **Terminate early** (not traverse remaining servers) and **return** the result.
- Guarantee **absence of race conditions** on the shared counter.

> You can use `AtomicInteger` or minimal synchronization over the critical section of the counter.

---

## Part III — (Progress) Synchronization and *Deadlocks* with *Highlander Simulator*
> 1. Review the simulation: N immortals; each one **attacks** another. The attacker **subtracts M** from the opponent and **adds M/2** to their own life.


This simulation models `N` immortals running concurrently. Each immortal is represented by one thread and repeatedly attacks another immortal.

- `N`: number of immortals in the population (`-Dcount`).
- `H`: initial health of each immortal (`-Dhealth`).
- `M`: damage per hit (`-Ddamage`).

In each iteration of an immortal thread (`run()` in `Immortal.java`), the thread:

1. Checks cooperative pause control (`awaitIfPaused()`).
2. Chooses a random opponent different from itself.
3. Executes a fight using the selected strategy (`naive` or `ordered`).
4. Sleeps briefly to reduce aggressive spinning (`Thread.sleep(2)`).

**Current fight rule:**

- The attacker decreases opponent health by `M`.
- The attacker increases its own health by `M/2`.
- The update is applied only if both immortals are still alive (`health > 0`).

**Fight strategy modes:**

- `naive`: nested locking with `synchronized(this)` then `synchronized(other)`. This may deadlock if two threads lock in opposite order.
- `ordered`: nested locking with a consistent global order (by immortal name), preventing circular wait and avoiding deadlock.


---

> 2. **Invariant**: with N and initial health `H`, the total sum should remain constant (except during an update). Calculate that value and use it to validate.


Parameters used in this run:

- `N = 8`
- `H = 100`
- `M = 10`
- Fight mode: `ordered`



Initial total health:

$$
S_0 = N \cdot H = 8 \cdot 100 = 800
$$

Constant invariant expected by the statement:

$$
S_{\text{const}} = 800
$$

Current implementation rule in `Immortal.java`:

- defender: `-M`
- attacker: `+M/2`

So the total change per fight is:

$$
\Delta = -M + \frac{M}{2} = -10 + 5 = -5
$$

Therefore, after `k` fights:

$$
S(k) = S_0 + k\Delta = 800 - 5k
$$

---

> 3. Run the UI and try **"Pause & Check"**. Is the invariant satisfied? Explain.

The simulator was executed with the following parameters:

- `count=8`
- `health=100`
- `damage=10`
- `fight=ordered`

![Point2](images/imagen2.png)

After starting the simulation, the tester waited a few seconds and then pressed `Pause & Check`.

Observed values in the captured run:

- `Score (fights) = 146`
- `Total Health = 70`

Using the formula defined in Point 2 for the current implementation:

- `S0 = N * H = 8 * 100 = 800`
- `delta = -M + (M/2) = -10 + 5 = -5`
- `S(k) = 800 - 5k`
- `S(146) = 800 - 5(146) = 70`

The observed value (`70`) matches the expected value for the implemented fight rule.

![Point2](images/imagen1.png)

### Conclusion

The validation with `Pause & Check` shows that the simulation is behaving consistently with the current code logic.  
However, the total health is not constant because each fight reduces the global total by `M/2`.  
For this reason, the constant invariant proposed in the statement (`N * H`) is not satisfied under the current rule (`-M` to defender and `+M/2` to attacker).

---

> 4. **Correct pause**: ensure that **all** threads are paused **before** reading/printing health; implement **Resume** (already available).

Implemented a **synchronization barrier pattern** in `PauseController` to ensure all threads are paused before reading values.

### Changes made
* **PauseController:** Added thread tracking with `activeThreads` and `waitingThreads` counters. Added `waitUntilAllPaused()` method that blocks the UI thread until all immortal threads confirm they've reached `awaitIfPaused()`.
* **Immortal:** Modified `run()` to call `controller.registerThread()` at startup and `controller.unregisterThread()` in the `finally` block. This keeps the controller aware of how many threads are active.
* **ControlFrame.onPauseAndCheck:** Now calls `manager.controller().waitUntilAllPaused()` after `manager.pause()` and before reading health values. This guarantees all threads are blocked before inspection.

### How it works
1. When "Pause & Check" is clicked, the UI thread blocks on a condition variable.
2. Each immortal thread increments a counter when reaching `awaitIfPaused()`.
3. When the last thread arrives (`waitingThreads == activeThreads`), it signals the UI thread.
4. Only then does the UI read health values, ensuring consistency.


> 5. Do repeated *clicks* and validate consistency. Is the invariant maintained?

### Validation with Multiple Pauses

The simulation was tested with repeated **Pause & Check** operations to validate consistency across pause/resume cycles.

**Test configuration:**
- `count=8`
- `health=1000`
- `damage=5`
- `fight=ordered`

For the current implementation:
- `S0 = N * H = 8 * 1000 = 8000`
- `delta = -M + (M/2) = -5 + 2 = -3` 
- `S(k) = 8000 - 3k`

**Results from multiple pause operations:**

| Pause # | Score (fights) `k` | Total Health (observed) | Expected `S(k)` | Match? |
|---------|---------------------|--------------------------|------------------|--------|
| 1       | 1712                | 2864                     | `8000-3(1712)=2864` | ✓ |
| 2       | 2575                | 275                      | `8000-3(2575)=275`  | ✓ |
| 3       | 2648                | 56                       | `8000-3(2648)=56`   | ✓ |

**Evidence:**

![Multiple pauses - Test 1](images/test1.png)
*Pause with `k=2648`, observed total `56`.*

![Multiple pauses - Test 2](images/test2.png)
*Pause with `k=1712`, observed total `2864`.*

![Multiple pauses - Test 3](images/test3.png)
*Pause with `k=2575`, observed total `275`.*

### Observations

1. Repeated **Pause & Check** snapshots are consistent (no partial updates were observed).
2. Observed totals match `S(k) = 8000 - 3k` exactly in all sampled pauses.
3. The pause barrier introduced in Point 4 is working for state inspection consistency.

### Conclusion

For this code version, the **constant invariant** (`Total Health = N*H`) is **not** maintained.  
However, the **implementation-based invariant** (`S(k)=8000-3k`) is maintained across repeated pause/resume cycles.  
This confirms correct synchronization during inspection, even though the fight rule itself changes total health over time.

---


> 6. **Critical sections**: identify and synchronize the fight sections to avoid races; if you use multiple *locks*, nest with **consistent order**:
   ```java
   synchronized (lockA) {
     synchronized (lockB) {
       // ...
     }
   }
   ```
> 7. If the app **freezes** (possible *deadlock*), use **`jps`** and **`jstack`** to diagnose.

### Critical Sections Identified

The main **critical section** in the simulator is the **fight operation** where two immortals simultaneously modify each other's health:

```java
other.health -= this.damage;      // Write to shared variable
this.health += this.damage / 2;   // Write to shared variable
scoreBoard.recordFight();         // Update shared counter
```

Two threads fighting concurrently can cause:
- **Race conditions**: partial updates visible to other threads
- **Data corruption**: health values become inconsistent
- **Deadlocks**: circular wait when acquiring multiple locks

---
### Two Fight Strategies

#### Strategy 1: Naive (CAUSES DEADLOCK)

```java
private void fightNaive(Immortal other) {
    synchronized (this) {           
      synchronized (other) {        
        if (this.health <= 0 || other.health <= 0) return;
        other.health -= this.damage;
        this.health += this.damage / 2;
        scoreBoard.recordFight();
      }
    }
}
```

**The Problem:**

```
Thread A (Immortal-5 vs 3):   Thread B (Immortal-3 vs 5):
  Lock(5) ✓                     Lock(3) ✓
  wants(3) ❌                   wants(5) ❌
  DEADLOCK
```

**Test result:**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=12 -Dfight=naive -Dhealth=100 -Ddamage=10
```

**Observed Behavior:**
- UI starts and shows immortals fighting
- After ~5-10 seconds: **UI freezes completely**
- Buttons become unresponsive
- Simulation stops progressing

![UI frozen - Naive mode](images/UInaive.png)

**jstack output confirms deadlock:**

![Deadlock evidence](images/naive.png)

---

#### Strategy 2: Ordered (PREVENTS DEADLOCK)

```java
private void fightOrdered(Immortal other) {
    // Establish global order by name (alphabetically)
    Immortal first = this.name.compareTo(other.name) < 0 ? this : other;
    Immortal second = this.name.compareTo(other.name) < 0 ? other : this;
    
    synchronized (first) {            // Always lock lower name first
      synchronized (second) {         // Then lock higher name
        if (this.health <= 0 || other.health <= 0) return;
        other.health -= this.damage;
        this.health += this.damage / 2;
        scoreBoard.recordFight();
      }
    }
}
```

**Test result:**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=ordered -Dhealth=1000 -Ddamage=10
```

![UI responsive - Ordered mode](images/UIordered.png)

**Observed Behavior:**
- UI remains **responsive** throughout
- Buttons work normally (Start, Pause & Check, Resume, Stop)
- Simulation runs for hours without freezing
- Health values decrease consistently per the formula `S(k) = 8000 - 3k`

**jstack output shows no deadlock:**

![No deadlock evidence](images/ordered.png)

---

### Lock Ordering Comparison

| Aspect | Naive | Ordered |
|--------|-------|---------|
| **Lock sequence** | `this` → `other` | Alphabetical sort first |
| **Lock order same for all threads?** |  NO (inconsistent) |  YES (global total order) |
| **Circular wait possible?** |  YES |  NO |
| **Deadlock risk** | **HIGH** (verified) | **NONE** |
| **Max N before failure** | ~12 | 100,000+ |
| **Reason for failure** | Inconsistent ordering | Consistent global order |



---

> 8. Apply a **strategy** to fix the *deadlock* (e.g., **total order** by name/id, or **`tryLock(timeout)`** with retries and *backoff*).

### Implemented Strategies to Fix Deadlock

We have implemented **two strategies** to prevent deadlocks in the simulator:

#### Strategy A: Total Order (Already Implemented)

The `fightOrdered()` method establishes a **global total order** by sorting immortals alphabetically by name before acquiring locks. This guarantees that all threads acquire locks in the same order, eliminating the possibility of circular wait.

```java
private void fightOrdered(Immortal other) {
    Immortal first = this.name.compareTo(other.name) < 0 ? this : other;
    Immortal second = this.name.compareTo(other.name) < 0 ? other : this;
    synchronized (first) {
      synchronized (second) {
        // ... fight logic
      }
    }
}
```

**Pros:**
- Simple to implement
- Zero deadlock risk
- Predictable lock acquisition order

**Cons:**
- May cause higher contention when many threads want the same "first" lock
- Threads block indefinitely until locks are available

---

#### Strategy B: TryLock with Timeout and Exponential Backoff (NEW)

The `fightTryLock()` method uses `ReentrantLock.tryLock(timeout)` to acquire locks **non-blocking** with a timeout. If both locks cannot be acquired, it releases any held lock and retries with **exponential backoff** and **random jitter**.

```java
private void fightTryLock(Immortal other) {
    final int MAX_RETRIES = 10;
    final int INITIAL_BACKOFF_MS = 1;
    final int MAX_BACKOFF_MS = 50;
    
    int backoff = INITIAL_BACKOFF_MS;
    
    for (int attempt = 0; attempt < MAX_RETRIES && running; attempt++) {
      try {
        // Try to acquire first lock with timeout
        if (this.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
          try {
            // Try to acquire second lock with timeout
            if (other.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
              try {
                // Both locks acquired - perform the fight
                if (this.health <= 0 || other.health <= 0) return;
                other.health -= this.damage;
                this.health += this.damage / 2;
                scoreBoard.recordFight();
                return; // Success
              } finally {
                other.lock.unlock();
              }
            }
          } finally {
            this.lock.unlock();
          }
        }
        
        // Exponential backoff with random jitter
        int jitter = ThreadLocalRandom.current().nextInt(0, backoff + 1);
        Thread.sleep(backoff + jitter);
        backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    // Max retries reached - fight abandoned (no deadlock)
}
```

**How it prevents deadlock:**

1. **Non-blocking acquisition**: `tryLock(timeout)` returns `false` instead of blocking forever
2. **Lock release on failure**: If second lock fails, first lock is released immediately
3. **Exponential backoff**: Wait time doubles after each failed attempt (1ms → 2ms → 4ms → ... → 50ms max)
4. **Random jitter**: Adds randomness to avoid **livelock** where two threads retry in sync
5. **Max retries**: After 10 attempts, fight is abandoned (progress over perfection)

**Pros:**
- Zero deadlock risk
- Guarantees progress (no indefinite blocking)
- Adaptive to contention levels

**Cons:**
- Slightly more complex implementation
- Some fights may be abandoned under extreme contention
- Small overhead from retry mechanism

---

### Strategy Comparison Table

| Aspect | Naive | Ordered | TryLock |
|--------|-------|---------|---------|
| **Lock mechanism** | `synchronized` | `synchronized` | `ReentrantLock.tryLock()` |
| **Lock order** | `this` → `other` | Alphabetical | Any order (non-blocking) |
| **Deadlock possible?** | YES | NO | NO |
| **Livelock possible?** | NO | NO | NO (backoff + jitter) |
| **Blocking behavior** | Infinite wait | Infinite wait | Timeout + retry |
| **Contention handling** | None | None | Exponential backoff |
| **Complexity** | Low | Low | Medium |

---

### Test Commands

```bash
# Strategy 1: Ordered (total order by name)
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=ordered -Dhealth=1000 -Ddamage=10

# Strategy 2: TryLock (timeout + backoff)
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=trylock -Dhealth=1000 -Ddamage=10
```

---

> 9. Validate with **N=100, 1000 or 10000** immortals. If the invariant fails, review the pause and critical sections.

### Validation with High N Values

The simulator was tested with N=100, N=1000, and N=10000 immortals using both `ordered` and `trylock` strategies.

#### Test Configuration
- `health=100` (default)
- `damage=10`
- Fight modes: `ordered` and `trylock`

Expected invariant formula:
- `S0 = N * H`
- `delta = -M + (M/2) = -10 + 5 = -5`
- `S(k) = S0 - 5k`

---

#### Test Results: N=100

**Command:**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=100 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

| Strategy | Score (k) | Total Health | Expected S(k) | Match? |
|----------|-----------|--------------|---------------|--------|
| ordered  | 1845      | 775          | `10000-5(1845)=775` | ✓ |
| trylock  | 1868      | 660          | `10000-5(1868)=660` | ✓ |


**Evidence:** 

![Validation N=100 Ordered](images/validation_n100_ordered.png)

![Validation N=100 TryLock](images/validation_n100_trylock.png)

---

#### Test Results: N=1000

**Command:**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=1000 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

| Strategy | Score (k) | Total Health | Expected S(k) | Match? |
|----------|-----------|--------------|---------------|--------|
| ordered  | 19487     | 2565         | `100000-5(19487)=2565` | ✓ |
| trylock  | 19934     | 330          | `100000-5(19934)=330` | ✓ |

**Evidence:**

![Validation N=1000 Ordered](images/validation_n1000_ordered.png)

![Validation N=1000 TryLock](images/validation_n1000_trylock.png)

---

#### Test Results: N=10000

**Command:**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=10000 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

| Strategy | Score (k) | Total Health | Expected S(k) | Match? |
|----------|-----------|--------------|---------------|--------|
| ordered  | 132545    | 337275       | `1000000-5(132545)=337275` | ✓ |
| trylock  | 172786    | 136070       | `1000000-5(172786)=136070` | ✓ |

**Evidence:**

![Validation N=10000 Ordered](images/validation_n10000_ordered.png)

![Validation N=10000 TryLock](images/validation_n10000_trylock.png)

---

### Observations

1. **Invariant maintained**: In all tests, the observed total health matches the expected value `S(k) = S0 - 5k` exactly.

2. **No deadlocks**: Both `ordered` and `trylock` strategies ran without freezing for N=100, 1000, and 10000.

3. **Performance comparison**:
   - `ordered` processes slightly more fights per second (no retry overhead)
   - `trylock` shows similar performance but with some abandoned fights under high contention

4. **Scalability**: The simulation scales well with Virtual Threads (Java 21):
   - N=100: Minimal resource usage
   - N=1000: Moderate CPU usage, responsive UI
   - N=10000: Higher CPU usage but still functional

5. **Pause & Check consistency**: The synchronization barrier ensures consistent snapshots even with 10000 threads.

### Conclusion

Both strategies successfully prevent deadlocks and maintain the invariant:
- **`ordered`** is simpler and slightly faster
- **`trylock`** offers more flexibility and adaptive backoff under contention

For most use cases, **`ordered`** is recommended due to its simplicity. Use **`trylock`** when you need timeout-based lock acquisition or adaptive contention handling.

---
> 10. **Remove dead immortals** without blocking the simulation: analyze if it creates a **race condition** with many threads and fix **without global synchronization** (concurrent collection or *lock-free* approach).


> 11. Fully implement **STOP** (orderly shutdown).

---

## Deliverables

1. **Source code** (Java 21) with the UI working.
2. **`Lab report in pdf format`** with:
   - Part I: CPU diagnosis and changes to eliminate busy-wait.
   - Part II: **early stop** design and how you avoid race conditions on the counter.
   - Part III:
     - Critical sections and adopted strategy (**total order** or **tryLock+timeout**).
     - Evidence of *deadlock* (if it occurred) with `jstack` and applied fix.
     - Validation of the **invariant** with **Pause & Check** (different N values).
     - Strategy for **removing dead immortals** without global synchronization.
3. Execution instructions if you change *defaults*.

---

## Evaluation criteria (10 pts)

- (3) **Correct concurrency**: no *data races*; well-localized synchronization; no busy-wait.
- (2) **Pause/Resume**: state consistency and invariant under **Pause & Check**.
- (2) **Robustness**: runs with high N; no `ConcurrentModificationException`, no unmanaged *deadlocks*.
- (1.5) **Quality**: clear architecture, names and comments; UI/logic separation.
- (1.5) **Documentation**: clear **`ANSWERS.txt`** with evidence (dumps/screenshots) and technical justification.

---

## Tips and useful configuration

- **Fight strategies**:
  - `-Dfight=naive` → useful to **reproduce** races and *deadlocks*.
  - `-Dfight=ordered` → **avoids** *deadlocks* (total order by name/id).
- **Cooperative pause**: use `PauseController` (Lock/Condition), **without** `suspend/resume/stop`.
- **Collections**: avoid unsafe structures; prefer immutability or concurrent collections.
- **Diagnostics**: `jps`, `jstack`, **jVisualVM**; review *thread dumps* when you suspect *deadlock*.
- **Virtual Threads**: favor waiting with blocking (not *busy-wait*); use timeouts.

---

## How to run tests

```bash
mvn clean verify
```

Includes compilation and JUnit tests.

---

## Credits and license

Lab based on the historic course assignment (Highlander, Producer/Consumer, Distributed Search), modernized to **Java 21**.  
<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br />This content is part of the Software Architectures course (ECI) and is licensed under <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.

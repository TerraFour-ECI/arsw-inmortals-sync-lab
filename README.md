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
- `-Dfight=ordered|naive` → fight strategy (`ordered` avoids *deadlocks*, `naive` may cause them)
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



> 2. **Invariant**: with N and initial health `H`, the total sum should remain constant (except during an update). Calculate that value and use it to validate.


Parameters used in this run:

- `N = 8`
- `H = 100`
- `M = 10`
- Fight mode: `ordered`

![Point2](images/imagen2.png)

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

For `k = 146`:

$$
S(146) = 800 - 5(146) = 70
$$

Observed value in UI:

- `Total Health = 70`
- `Score (fights) = 146`

![Point2](images/imagen1.png)

**Conclusion:**

- The constant invariant (`Total Health = 800`) does not hold with the current fight rule.
- The code-based invariant ($$S(k)=800-5k$$) matches the observed result.





> 3. Run the UI and try **"Pause & Check"**. Is the invariant satisfied? Explain.
> 4. **Correct pause**: ensure that **all** threads are paused **before** reading/printing health; implement **Resume** (already available).
> 5. Do repeated *clicks* and validate consistency. Is the invariant maintained?
> 6. **Critical sections**: identify and synchronize the fight sections to avoid races; if you use multiple *locks*, nest with **consistent order**:
   ```java
   synchronized (lockA) {
     synchronized (lockB) {
       // ...
     }
   }
   ```
> 7. If the app **freezes** (possible *deadlock*), use **`jps`** and **`jstack`** to diagnose.
> 8. Apply a **strategy** to fix the *deadlock* (e.g., **total order** by name/id, or **`tryLock(timeout)`** with retries and *backoff*).
> 9. Validate with **N=100, 1000 or 10000** immortals. If the invariant fails, review the pause and critical sections.
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

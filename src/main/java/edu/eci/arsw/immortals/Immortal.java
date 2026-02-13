package edu.eci.arsw.immortals;

import edu.eci.arsw.concurrency.PauseController;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class Immortal implements Runnable {
  private final String name;
  private int health;
  private final int damage;
  private final List<Immortal> population;
  private final ScoreBoard scoreBoard;
  private final PauseController controller;
  private volatile boolean running = true;
  private final ReentrantLock lock = new ReentrantLock();

  public Immortal(String name, int health, int damage, List<Immortal> population, ScoreBoard scoreBoard, PauseController controller) {
    this.name = Objects.requireNonNull(name);
    this.health = health;
    this.damage = damage;
    this.population = Objects.requireNonNull(population);
    this.scoreBoard = Objects.requireNonNull(scoreBoard);
    this.controller = Objects.requireNonNull(controller);
  }

  public String name() {
    return name;
  }

  public synchronized int getHealth() {
    return health;
  }

  public boolean isAlive() {
    return getHealth() > 0 && running;
  }

  public void stop() {
    running = false;
  }

  public ReentrantLock getLock() {
    return lock;
  }

  @Override
  public void run() {
    controller.registerThread();
    try {
      while (running && isAlive() && !Thread.currentThread().isInterrupted()) {
        controller.awaitIfPaused();
        if (!running || Thread.currentThread().isInterrupted()) break;
        var opponent = pickOpponent();
        if (opponent == null) continue;
        String mode = System.getProperty("fight", "ordered");
        if ("naive".equalsIgnoreCase(mode)) fightNaive(opponent);
        else if ("trylock".equalsIgnoreCase(mode)) fightTryLock(opponent);
        else fightOrdered(opponent);
        Thread.sleep(2);
      }
      if (!isAlive()) {
        population.remove(this);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } finally {
      controller.unregisterThread();
    }
  }

  private Immortal pickOpponent() {
    if (population.size() <= 1) return null;
    for (int attempts = 0; attempts < 10; attempts++) {
      Immortal other = population.get(ThreadLocalRandom.current().nextInt(population.size()));
      if (other != this && other.isAlive()) {
        return other;
      }
    }
    return null;
  }

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

  private void fightOrdered(Immortal other) {
    Immortal first = this.name.compareTo(other.name) < 0 ? this : other;
    Immortal second = this.name.compareTo(other.name) < 0 ? other : this;
    synchronized (first) {
      synchronized (second) {
        if (this.health <= 0 || other.health <= 0) return;
        other.health -= this.damage;
        this.health += this.damage / 2;
        scoreBoard.recordFight();
      }
    }
  }

  /**
   * Fight strategy using tryLock with timeout and exponential backoff.
   * This approach prevents deadlocks by:
   * 1. Using tryLock with a timeout instead of blocking indefinitely
   * 2. Releasing all locks if we can't acquire both
   * 3. Using exponential backoff to reduce contention
   * 4. Adding random jitter to avoid livelock
   */
  private void fightTryLock(Immortal other) {
    final int MAX_RETRIES = 10;
    final int INITIAL_BACKOFF_MS = 1;
    final int MAX_BACKOFF_MS = 50;

    int backoff = INITIAL_BACKOFF_MS;

    for (int attempt = 0; attempt < MAX_RETRIES && running; attempt++) {
      try {
        if (this.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
          try {
            if (other.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
              try {
                if (this.health <= 0 || other.health <= 0) return; // Both locks acquired then fight
                other.health -= this.damage;
                this.health += this.damage / 2;
                scoreBoard.recordFight();
                return;
              } finally {
                other.lock.unlock();
              }
            }
          } finally {
            this.lock.unlock();
          }
        }

        int jitter = ThreadLocalRandom.current().nextInt(0, backoff + 1);
        Thread.sleep(backoff + jitter);
        backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    // no deadlock, just contention
  }
}

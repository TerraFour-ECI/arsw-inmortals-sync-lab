package edu.eci.arsw.immortals;

import edu.eci.arsw.concurrency.PauseController;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class Immortal implements Runnable {
  private final String name;
  private int health;
  private final int damage;
  private final List<Immortal> population;
  private final ScoreBoard scoreBoard;
  private final PauseController controller;
  private volatile boolean running = true;

  public Immortal(String name, int health, int damage, List<Immortal> population, ScoreBoard scoreBoard, PauseController controller) {
    this.name = Objects.requireNonNull(name);
    this.health = health;
    this.damage = damage;
    this.population = Objects.requireNonNull(population);
    this.scoreBoard = Objects.requireNonNull(scoreBoard);
    this.controller = Objects.requireNonNull(controller);
  }

  public String name() { return name; }
  public synchronized int getHealth() { return health; }
  public boolean isAlive() { return getHealth() > 0 && running; }
  public void stop() { running = false; }

  @Override public void run() {
    controller.registerThread();
    try {
      while (running) {
        controller.awaitIfPaused();
        if (!running) break;
        var opponent = pickOpponent();
        if (opponent == null) continue;
        String mode = System.getProperty("fight", "ordered");
        if ("naive".equalsIgnoreCase(mode)) fightNaive(opponent);
        else fightOrdered(opponent);
        Thread.sleep(2);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } finally {
      controller.unregisterThread();
    }
  }

  private Immortal pickOpponent() {
    if (population.size() <= 1) return null;
    Immortal other;
    do {
      other = population.get(ThreadLocalRandom.current().nextInt(population.size()));
    } while (other == this);
    return other;
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
}

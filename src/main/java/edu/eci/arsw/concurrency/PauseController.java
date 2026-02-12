package edu.eci.arsw.concurrency;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class PauseController {
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition allPaused = lock.newCondition();
  private final Condition unpaused = lock.newCondition();
  private volatile boolean paused = false;

  private int activeThreads = 0;  
  private int waitingThreads = 0;    

  public void pause() {
    lock.lock(); 
    try { 
      paused = true; 
    } finally { 
      lock.unlock(); 
    } 
  }

  public void resume() { 
    lock.lock(); 
    try { 
      paused = false; 
      waitingThreads = 0;  
      unpaused.signalAll(); 
    } finally { 
      lock.unlock(); 
    } 
  }

  public boolean paused() { 
    return paused; 
  }

  public void registerThread() {
    lock.lock();
    try {
      activeThreads++;
    } finally {
      lock.unlock();
    }
  }

  public void unregisterThread() {
    lock.lock();
    try {
      activeThreads--;
      if (paused && waitingThreads == activeThreads) {
        allPaused.signalAll(); 
      }
    } finally {
      lock.unlock();
    }
  }

  public void waitUntilAllPaused() throws InterruptedException {
    lock.lockInterruptibly();
    try {
      while (paused && waitingThreads < activeThreads) {
        allPaused.await();
      }
    } finally {
      lock.unlock();
    }
  }

  public void awaitIfPaused() throws InterruptedException {
    lock.lockInterruptibly();
    try { 
      if (paused) {
        waitingThreads++;  
        if (waitingThreads == activeThreads) {
          allPaused.signalAll(); 
        }
        while (paused) {
          unpaused.await();
        }
        waitingThreads--;  
      }
    } finally { 
      lock.unlock(); 
    }
  }
}

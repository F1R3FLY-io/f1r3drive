package io.f1r3fly.f1r3drive.blockchain.client.background;

import java.util.concurrent.ConcurrentLinkedDeque;

public class BlockingConcurrentLinkedDeque<E> {
    private final ConcurrentLinkedDeque<E> deque = new ConcurrentLinkedDeque<>();

    public synchronized E poll() throws InterruptedException {
        while (deque.isEmpty()) {
            wait();
        }
        return deque.poll();
    }

    public synchronized void add(E e) {
        deque.add(e);
        notifyAll();
    }

    public synchronized void addFirst(E e) {
        deque.addFirst(e);
        notifyAll();
    }

    public synchronized boolean isEmpty() {
        return deque.isEmpty();
    }

    public synchronized void wainUntilNotEmpty() throws InterruptedException {
        while (!deque.isEmpty()) {
            wait();
        }
    }

    public synchronized int size() {
        return deque.size();
    }

    public synchronized void clear() {
        deque.clear();
    }
}
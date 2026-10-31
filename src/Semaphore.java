public class Semaphore {

    private int value;

    public Semaphore(int initial) {
        this.value = initial;
    }

    public synchronized void acquire() throws InterruptedException {
    }

    public synchronized void release() {
    }

    public synchronized boolean tryAcquire(long timeoutMillis) throws InterruptedException {
        return false;
    }

    public synchronized int getValue() {
        return value;
    }
}

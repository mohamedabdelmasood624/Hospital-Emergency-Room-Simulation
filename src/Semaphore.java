public class Semaphore {

    private int value;

    public Semaphore(int initial) {
        this.value = initial;
    }

    public synchronized void acquire() throws InterruptedException {
        while (value <= 0) {
            wait();
        }
        value--;
    }

    public synchronized void release() {
        value++;
        notifyAll();
    }

    public synchronized boolean tryAcquire(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long remainingTime = timeoutMillis;

        while (value <= 0) {
            if (remainingTime <= 0) {
                return false;
            }
            wait(remainingTime);
            remainingTime = timeoutMillis - (System.currentTimeMillis() - startTime);
        }
        value--;
        return true;
    }

    public synchronized int getValue() {
        return value;
    }
}
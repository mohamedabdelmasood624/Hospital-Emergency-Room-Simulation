import java.util.LinkedList;
import java.util.Queue;

public class WaitingRoom {

    private final int capacity;
    private final Queue<Patient> queue;
    private final Semaphore emptySlots;
    private final Semaphore fullSlots;
    private final Semaphore mutex;

    public WaitingRoom(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedList<>();
        this.emptySlots = new Semaphore(capacity);
        this.fullSlots = new Semaphore(0);
        this.mutex = new Semaphore(1);
    }

    public void enter(Patient patient) throws InterruptedException {
    }

    public void returnPatient(Patient patient) throws InterruptedException {
    }

    public Patient takePatient() throws InterruptedException {
        return null;
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized int getCurrentPatientCount() {
        return queue.size();
    }
}

import java.util.LinkedList;
import java.util.Queue;

public class WaitingRoom {

    private final int capacity;
    private final Queue<Patient> queue;
    private final Queue<Patient> street;
    private final Semaphore emptySlots;
    private final Semaphore fullSlots;
    private final Semaphore mutex;

    public WaitingRoom(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedList<>();
        this.street = new LinkedList<>();
        this.emptySlots = new Semaphore(capacity);
        this.fullSlots = new Semaphore(0);
        this.mutex = new Semaphore(1);
    }

    public void enter(Patient patient) throws InterruptedException {
        Hospital.log(patient.getPatientName() + " arrived.");

        if (emptySlots.getValue() <= 0) Hospital.log("Waiting room is full.");
        
        
        emptySlots.acquire();
        mutex.acquire();
        
        try {
            queue.add(patient);
            Hospital.log(patient.getPatientName() + " entered waiting room");
        } finally {
            mutex.release();
        }
        fullSlots.release();
    }

    public void returnPatient(Patient patient) throws InterruptedException {
        emptySlots.acquire();
        mutex.acquire();
        try {
            queue.add(patient);
            Hospital.log(patient.getPatientName() + " returned to waiting room (timeout)");
        } finally {
            mutex.release();
        }
        fullSlots.release();
    }

    public Patient takePatient(Doctor curdoc) throws InterruptedException {
        fullSlots.acquire();
        mutex.acquire();
        try {
            Patient patient = queue.poll();
            // if (patient != null) {
            // Hospital.log(patient.getPatientName() + " taken from waiting room");
            // }
            return patient;
        } finally {
            Hospital.log(curdoc.getDoctorName() + " is now available");
            mutex.release();
            emptySlots.release();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized int getCurrentPatientCount() {
        return queue.size();
    }
}

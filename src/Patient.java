public class Patient extends Thread {

    private final int id;
    private final WaitingRoom waitingRoom;

    public Patient(int id, WaitingRoom waitingRoom) {
        this.id = id;
        this.waitingRoom = waitingRoom;
    }

    @Override
    public void run() {
        try {
            waitingRoom.enter(this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getPatientId() {
        return id;
    }

    public String getPatientName() {
        return "P" + id;
    }
}

public class TreatmentRoom {

    private final int roomId;
    private boolean occupied;
    private int currentDoctorId;
    private int currentPatientId;

    public TreatmentRoom(int roomId) {
        this.roomId = roomId;
        this.occupied = false;
        this.currentDoctorId = -1;
        this.currentPatientId = -1;
    }

    public int getRoomId() {
        return roomId;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public synchronized void occupy(int doctorId, int patientId) {
        this.occupied = true;
        this.currentDoctorId = doctorId;
        this.currentPatientId = patientId;
    }

    public synchronized void vacate() {
        this.occupied = false;
        this.currentDoctorId = -1;
        this.currentPatientId = -1;
    }

    public int getCurrentDoctorId() {
        return currentDoctorId;
    }

    public int getCurrentPatientId() {
        return currentPatientId;
    }
}

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

    public void occupy(int doctorId, int patientId) {
    }

    public void vacate() {
    }

    public int getCurrentDoctorId() {
        return currentDoctorId;
    }

    public int getCurrentPatientId() {
        return currentPatientId;
    }
}

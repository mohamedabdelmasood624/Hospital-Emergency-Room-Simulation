public class Doctor extends Thread {

    private final int id;
    private final WaitingRoom waitingRoom;
    private final TreatmentRoomPool treatmentRoomPool;

    public Doctor(int id, WaitingRoom waitingRoom, TreatmentRoomPool treatmentRoomPool) {
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentRoomPool = treatmentRoomPool;
    }

    @Override
    public void run() {
    }

    public int getDoctorId() {
        return id;
    }

    public String getDoctorName() {
        return "Doctor " + id;
    }
}

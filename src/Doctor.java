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

        while (!isInterrupted()) {
            Hospital.log(getDoctorName() + " is now available");
            try {
                Patient current3ayan = waitingRoom.takePatient();
                if (current3ayan != null) {
                    Hospital.log (getDoctorName()+ " is assigned to " + current3ayan.getPatientName());
                    Hospital.log(getDoctorName()+ " is trying to acquire a treatment room.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }

    public int getDoctorId() {
        return id;
    }

    public String getDoctorName() {
        return "Doctor " + id;
    }
}

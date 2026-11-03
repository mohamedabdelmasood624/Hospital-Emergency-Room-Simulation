import java.util.Random;

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
                    Hospital.log(getDoctorName() + " is assigned to " + current3ayan.getPatientName());
                    Hospital.log(getDoctorName() + " is trying to acquire a treatment room.");
                    TreatmentRoom room = treatmentRoomPool.acquireRoomWithTimeout(3000, id,
                            current3ayan.getPatientId());
                    if (room != null) {
                        Hospital.log(getDoctorName() + " acquired a treatment room.");
                        int treatmentTime = 1000 + (int) (Math.random() * 2001);
                        Hospital.log(getDoctorName() + " is treating " + current3ayan.getPatientName() + " for "
                                + treatmentTime + " ms.");
                        Thread.sleep(treatmentTime);

                        Hospital.log(getDoctorName() + " finished treating " + current3ayan.getPatientName() + ".");
                        treatmentRoomPool.releaseRoom(room);
                        Hospital.log(getDoctorName() + " released the treatment room.");
                    } else {
                        Hospital.log(getDoctorName() + " could not acquire a treatment room.");
                        Hospital.log(getDoctorName() + " returning " + current3ayan.getPatientName()
                                + " to the waiting room.");
                        waitingRoom.returnPatient(current3ayan);
                    }
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
import java.util.Random;

public class Doctor extends Thread {

    private final int id;
    private final WaitingRoom waitingRoom;
    private final TreatmentRoomPool treatmentRoomPool;
    private final Random random = new Random();

    private static final long ROOM_ACQUIRE_TIMEOUT_MS = 3000;
    private static final int MIN_TREATMENT_TIME_MS = 1000;
    private static final int MAX_TREATMENT_TIME_MS = 3000;

    public Doctor(int id, WaitingRoom waitingRoom, TreatmentRoomPool treatmentRoomPool) {
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentRoomPool = treatmentRoomPool;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Patient patient = waitingRoom.takePatient();
                if (patient == null) {
                    continue;
                }

                Hospital.log(getDoctorName() + " attempting to acquire treatment room for " + patient.getPatientName());

                TreatmentRoom room = treatmentRoomPool.acquireRoomWithTimeout(
                    ROOM_ACQUIRE_TIMEOUT_MS,
                    id,
                    patient.getPatientId()
                );

                if (room == null) {
                    Hospital.log(getDoctorName() + " timeout acquiring room for " + patient.getPatientName() + " - returning to waiting room");
                    waitingRoom.returnPatient(patient);
                } else {
                    Hospital.log(getDoctorName() + " acquired Room " + room.getRoomId() + " for " + patient.getPatientName() + " - starting treatment");

                    int treatmentTime = MIN_TREATMENT_TIME_MS + random.nextInt(MAX_TREATMENT_TIME_MS - MIN_TREATMENT_TIME_MS + 1);
                    Hospital.log(getDoctorName() + " treating " + patient.getPatientName() + " in Room " + room.getRoomId() + " for " + treatmentTime + "ms");

                    Thread.sleep(treatmentTime);

                    treatmentRoomPool.releaseRoom(room);
                    Hospital.log(getDoctorName() + " completed treatment for " + patient.getPatientName() + " - released Room " + room.getRoomId());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        while (!isInterrupted()) {
            Hospital.log(getDoctorName() + " is now available");
            try {
                Patient current3ayan = waitingRoom.takePatient();
                if (current3ayan != null) {
                    Hospital.log (getDoctorName()+ " is assigned to " + current3ayan.getPatientName());
                    Hospital.log(getDoctorName()+ " is trying to acquire a treatment room.");
                    TreatmentRoom room = treatmentRoomPool.acquireRoomWithTimeout(3000, id, current3ayan.getPatientId());
                    if (room != null) {
                        Hospital.log(getDoctorName() + " acquired a treatment room.");
                    } else {
                        Hospital.log(getDoctorName() + " could not acquire a treatment room.");
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

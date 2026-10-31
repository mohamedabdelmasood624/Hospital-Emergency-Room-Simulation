public class TreatmentRoomPool {

    public static final int TOTAL_ROOMS = 2;

    private final TreatmentRoom[] rooms;
    private final Semaphore roomSemaphore;
    private final Semaphore mutex;

    public TreatmentRoomPool() {
        this.rooms = new TreatmentRoom[TOTAL_ROOMS];
        for (int i = 0; i < TOTAL_ROOMS; i++) {
            this.rooms[i] = new TreatmentRoom(i + 1);
        }
        this.roomSemaphore = new Semaphore(TOTAL_ROOMS);
        this.mutex = new Semaphore(1);
    }

    public TreatmentRoom acquireRoomWithTimeout(long timeoutMillis, int doctorId, int patientId) throws InterruptedException {
        return null;
    }

    public void releaseRoom(TreatmentRoom room) throws InterruptedException {
    }

    public TreatmentRoom[] getRooms() {
        return rooms;
    }
}

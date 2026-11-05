public class TreatmentRoomPool {

    public static final int total_rooms = 2;

    private final TreatmentRoom[] rooms;
    private final Semaphore roomSemaphore;
    private final Semaphore mutex;

    public TreatmentRoomPool() {
        this.rooms = new TreatmentRoom[total_rooms];
        for (int i = 0; i < total_rooms; i++) {
            this.rooms[i] = new TreatmentRoom(i + 1);
        }
        this.roomSemaphore = new Semaphore(total_rooms);
        this.mutex = new Semaphore(1);
    }

    public TreatmentRoom acquireRoomWithTimeout(long timeoutMillis, int doctorId, int patientId)
            throws InterruptedException {
        boolean acquired = roomSemaphore.tryAcquire(timeoutMillis);
        if (!acquired) {
            return null;
        }

        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            roomSemaphore.release();
            throw e;
        }

        try {
            for (TreatmentRoom room : rooms) {
                if (!room.isOccupied()) {
                    room.occupy(doctorId, patientId);
                    return room;
                }
            }
            roomSemaphore.release();
            return null;
        } finally {
            mutex.release();
        }
    }

    public void releaseRoom(TreatmentRoom room) throws InterruptedException {
        mutex.acquire();
        try {
            room.vacate();
        } finally {
            mutex.release();
        }
        roomSemaphore.release();
    }

    public TreatmentRoom[] getRooms() {
        return rooms.clone();
    }
}

import java.util.Scanner;

public class Hospital {

    private final int waitingRoomCapacity;
    private final int numDoctors;
    private final int numPatients;

    private final WaitingRoom waitingRoom;
    private final TreatmentRoomPool treatmentRoomPool;
    private final Doctor[] doctors;
    private final Patient[] patients;

    public Hospital(int waitingRoomCapacity, int numDoctors, int numPatients) {
        this.waitingRoomCapacity = waitingRoomCapacity;
        this.numDoctors = numDoctors;
        this.numPatients = numPatients;

        this.waitingRoom = new WaitingRoom(waitingRoomCapacity);
        this.treatmentRoomPool = new TreatmentRoomPool();
        this.doctors = new Doctor[numDoctors];
        this.patients = new Patient[numPatients];
    }

    public static synchronized void log(String message) {
        System.out.println(message);
    }

    public static synchronized void space(){
        System.out.println();
        System.out.println();
    }

    public void startSimulation() {
        for (int i = 0; i < numDoctors; i++) doctors[i] = new Doctor(i + 1, waitingRoom, treatmentRoomPool);
        for (int i = 0; i < numPatients; i++) patients[i] = new Patient(i + 1, waitingRoom);

        log("Patients arriving: ");
        for(int i = 0; i < numPatients; i++){
            System.out.println("P" + patients[i].getPatientId()+ " ");
        }
        space();

        for(int i = 0; i < numDoctors; i++) doctors[i].start();
        for(int i = 0; i < numPatients; i++) patients[i].start();


        for (Patient patient : patients) {
            try {
                patient.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        
        for (TreatmentRoom room : treatmentRoomPool.getRooms()) {
            if (room.isOccupied()) {
                log("Room " + room.getRoomId() + ": Occupied by Doctor " + room.getCurrentDoctorId() + ", Patient " + room.getCurrentPatientId());
                space();
            } else {
                log("Room " + room.getRoomId() + ": Available");
                space();
            }
        }


        log("All patients have been treated.");
        log("Simulation Completed.");

        for(Doctor doctor: doctors) 
            doctor.interrupt();

    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Waiting room capacity: ");
        int waitingRoomCapacity = scanner.nextInt();

        System.out.print("Number of doctors: ");
        int numDoctors = scanner.nextInt();

        System.out.print("Number of patients: ");
        int numPatients = scanner.nextInt();

        space();

        Hospital hospital = new Hospital(waitingRoomCapacity, numDoctors, numPatients);
        hospital.startSimulation();

        scanner.close();
    }
}

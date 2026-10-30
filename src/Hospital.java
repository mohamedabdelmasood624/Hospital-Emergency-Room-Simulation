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

    public void startSimulation() {
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Waiting room capacity: ");
        int waitingRoomCapacity = scanner.nextInt();

        System.out.print("Number of doctors: ");
        int numDoctors = scanner.nextInt();

        System.out.print("Number of patients: ");
        int numPatients = scanner.nextInt();

        Hospital hospital = new Hospital(waitingRoomCapacity, numDoctors, numPatients);
        hospital.startSimulation();

        scanner.close();
    }
}

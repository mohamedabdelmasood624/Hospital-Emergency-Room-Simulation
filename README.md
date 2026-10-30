# Hospital Emergency Room Simulation

CS241 / SCS241: Operating Systems  
Faculty of Computing and Artificial Intelligence, Cairo University  
**Instructor:** Eng. Eman Salah  
Assignment 1: Hospital Emergency Room Simulation  

## Team Members
| Name | ID | Tasks |
| --- | --- | --- |
| | | |
| | | |
| | | |
| | | |
| | | |

## Project Overview
This project simulates a busy Hospital Emergency Room (ER) using the Producer–Consumer Problem and Bounded Buffer synchronization pattern in Java. The simulation coordinates patients and doctors across shared resources (waiting room and treatment rooms) while preventing race conditions and deadlocks using custom semaphores.

## Project Phases & Tasks

### Phase 1: Setup & Shared Resources Foundation
- **Task 1**: Implement the custom Semaphore class with standard operations.
- **Task 2**: Develop the custom timeout method inside the Semaphore class to attempt acquiring resources within a time limit.
- **Task 3**: Create the data structure and variables required for the limited-capacity Waiting Room.
- **Task 4**: Create the data structure and variables required for the two-room Treatment Room pool.
- **Task 5**: Build the Hospital class skeleton to read user inputs for waiting room capacity, number of doctors, and number of patients.

### Phase 2: Entities & Core Actions
- **Task 6**: Implement the Patient class thread initialization and arrival logic.
- **Task 7**: Implement the Patient logic to enter the waiting room or wait outside if full.
- **Task 8**: Implement the Doctor class thread initialization and idle state.
- **Task 9**: Implement the Doctor logic to take a patient from the waiting room and become assigned to them.
- **Task 10**: Set up the Hospital class logic to instantiate the specified number of doctors and patients.

### Phase 3: Synchronization & Advanced Logic
- **Task 11**: Apply Bounded Buffer Producer-Consumer synchronization to the Waiting Room access
- **Task 12**: Integrate the three-second timeout logic for doctors attempting to acquire a treatment room.
- **Task 13**: Implement the Doctor fallback flow to return the patient to the waiting room if the three-second timeout is reached.
- **Task 14**: Implement the Doctor success flow to begin treatment for a random period and subsequently release the room.
- **Task 15**: Apply independent synchronization logic to the Treatment Rooms to ensure a strict limit of two concurrent treatments.

### Phase 4: Output, Testing & Submission
- **Task 16**: Implement centralized console printing in the Hospital class to display all system activities precisely as specified.
- **Task 17**: Execute targeted concurrency tests to ensure no race conditions occur in the shared waiting and treatment rooms.
- **Task 18**: Execute targeted concurrency tests to verify deadlocks do not occur during resource allocation and release.
- **Task 19**: Conduct a full-team code review to ensure all members understand all parts of the code.
- **Task 20**: Prepare the final ZIP file strictly named A1_TAname_Group_ID1_ID2_ID3_ID4 and designate exactly one member to upload it before the deadline.

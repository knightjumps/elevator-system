package elevator.panel;
import elevator.domain.ElevatorCar;
/** Cabin-facing input; destination selection belongs to the passenger's current car. */
public final class ElevatorPanel {
    private final ElevatorCar car;
    public ElevatorPanel(ElevatorCar car) { this.car = car; }
    public void selectFloor(int floor) { car.addDestinationStop(floor); }
    public void openDoor() { car.openDoorIfStationary(); }
    public void closeDoor() { car.closeDoor(); }
    public void pressEmergencyStop() { car.emergencyStop(); }
}

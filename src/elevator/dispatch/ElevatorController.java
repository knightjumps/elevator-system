package elevator.dispatch;
import elevator.domain.ElevatorCar;
import elevator.model.Direction;
import elevator.model.HallRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
/** Coordinates shared hall calls. Cars retain ownership of their motion and queues. */
public final class ElevatorController {
    private final List<ElevatorCar> cars; private final Map<Integer, ElevatorCar> carsById; private final SchedulingStrategy schedulingStrategy;
    public ElevatorController(List<ElevatorCar> cars, SchedulingStrategy schedulingStrategy) {
        this.cars = List.copyOf(cars); this.carsById = cars.stream().collect(Collectors.toUnmodifiableMap(ElevatorCar::id, Function.identity())); this.schedulingStrategy = schedulingStrategy;
    }
    public synchronized void requestElevator(int floor, Direction direction) {
        HallRequest request = new HallRequest(floor, direction, Instant.now());
        ElevatorCar selected = schedulingStrategy.selectCar(cars, request);
        if (selected == null) throw new IllegalStateException("No eligible elevator is available");
        selected.addPickupStop(floor);
    }
    public void selectDestination(int elevatorId, int destinationFloor) { car(elevatorId).addDestinationStop(destinationFloor); }
    public void setMaintenanceMode(int elevatorId, boolean enabled) { car(elevatorId).setMaintenance(enabled); }
    public void emergencyStop(int elevatorId) { car(elevatorId).emergencyStop(); }
    private ElevatorCar car(int id) { ElevatorCar car = carsById.get(id); if (car == null) throw new IllegalArgumentException("Unknown elevator id: " + id); return car; }
}

package elevator.system;

import elevator.dispatch.ElevatorController;
import elevator.dispatch.SchedulingStrategy;
import elevator.domain.ElevatorCar;
import elevator.domain.Floor;
import elevator.security.ConsoleSecurityService;
import elevator.security.SecurityService;
import java.util.ArrayList;
import java.util.List;
/** Composition root: creates floors, cars, and their shared controller from configuration. */
public final class Building {
    private final List<Floor> floors; private final List<ElevatorCar> elevators; private final ElevatorController controller;
    private Building(List<Floor> floors, List<ElevatorCar> elevators, ElevatorController controller) { this.floors = List.copyOf(floors); this.elevators = List.copyOf(elevators); this.controller = controller; }
    public static Building create(int floorCount, int elevatorCount, int maxLoadKg, SchedulingStrategy strategy) {
        if (floorCount < 1 || elevatorCount < 1 || maxLoadKg < 1) throw new IllegalArgumentException("Floor count, elevator count, and capacity must be positive");
        SecurityService security = new ConsoleSecurityService(); List<ElevatorCar> cars = new ArrayList<>();
        for (int id = 1; id <= elevatorCount; id++) cars.add(new ElevatorCar(id, floorCount - 1, maxLoadKg, security));
        ElevatorController controller = new ElevatorController(cars, strategy); List<Floor> floors = new ArrayList<>();
        for (int floor = 0; floor < floorCount; floor++) floors.add(new Floor(floor, controller));
        return new Building(floors, cars, controller);
    }
    public List<Floor> floors() { return floors; }
    public List<ElevatorCar> elevators() { return elevators; }
    public ElevatorController controller() { return controller; }
}

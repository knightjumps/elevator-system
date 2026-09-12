import elevator.dispatch.ElevatorController;
import elevator.dispatch.NearestEligibleCarStrategy;
import elevator.domain.ElevatorCar;
import elevator.model.Direction;
import elevator.system.Building;

/** A small runnable scenario for the elevator-system LLD. */
public class Main {
    public static void main(String[] args) {
        Building building = Building.create(12, 3, 1_000, new NearestEligibleCarStrategy());
        ElevatorController controller = building.controller();
        controller.requestElevator(3, Direction.UP);
        controller.requestElevator(8, Direction.DOWN);
        controller.selectDestination(1, 10);
        for (int round = 0; round < 5; round++)
            for (ElevatorCar car : building.elevators())
                car.processNextStop();
        controller.setMaintenanceMode(3, true);
        controller.requestElevator(6, Direction.UP);
        for (ElevatorCar car : building.elevators())
            System.out.println(car.status());
    }
}

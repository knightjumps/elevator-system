package elevator.dispatch;
import elevator.domain.ElevatorCar;
import elevator.model.Direction;
import elevator.model.HallRequest;
import java.util.Comparator;
import java.util.List;
/** Baseline policy; a car already travelling toward the call receives a large bonus. */
public final class NearestEligibleCarStrategy implements SchedulingStrategy {
    public ElevatorCar selectCar(List<ElevatorCar> cars, HallRequest request) {
        return cars.stream().filter(car -> car.canAccept(request)).min(Comparator.comparingInt(car -> score(car, request))).orElse(null);
    }

    private int score(ElevatorCar car, HallRequest request) {
        int distance = Math.abs(car.currentFloor() - request.floor());
        boolean onTheWay = car.direction() == request.direction() &&
                ((request.direction() == Direction.UP && car.currentFloor() <= request.floor()) ||
                (request.direction() == Direction.DOWN && car.currentFloor() >= request.floor()));
        return onTheWay ? distance : distance + 100;
    }
}

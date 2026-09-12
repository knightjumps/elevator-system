package elevator.dispatch;
import elevator.domain.ElevatorCar;
import elevator.model.HallRequest;
import java.util.List;
/** Strategy pattern: dispatch policy changes without changing the controller. */
public interface SchedulingStrategy { ElevatorCar selectCar(List<ElevatorCar> cars, HallRequest request); }

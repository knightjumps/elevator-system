package elevator.panel;
import elevator.dispatch.ElevatorController;
import elevator.model.Direction;
/** Floor-facing input; it forwards a request rather than choosing a car itself. */
public final class HallPanel {
    private final int floor; private final ElevatorController controller;
    public HallPanel(int floor, ElevatorController controller) { this.floor = floor; this.controller = controller; }
    public void pressUp() { controller.requestElevator(floor, Direction.UP); }
    public void pressDown() { controller.requestElevator(floor, Direction.DOWN); }
}

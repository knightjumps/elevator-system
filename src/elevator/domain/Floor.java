package elevator.domain;

import elevator.dispatch.ElevatorController;
import elevator.panel.HallPanel;

public final class Floor {
    private final int number;
    private final HallPanel hallPanel;

    public Floor(int number, ElevatorController controller) {
        this.number = number;
        hallPanel = new HallPanel(number, controller);
    }

    public int number() {
        return number;
    }

    public HallPanel hallPanel() {
        return hallPanel;
    }
}

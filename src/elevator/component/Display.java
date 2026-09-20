package elevator.component;

import elevator.model.Direction;

/**
 * Passive view updated by the car after a state change.
 */
public final class Display {
    private int currentFloor;
    private Direction direction = Direction.NONE;
    private int loadKg;

    public synchronized void update(int floor, Direction newDirection, int newLoadKg) {
        currentFloor = floor;
        direction = newDirection;
        loadKg = newLoadKg;
    }

    public synchronized String read() {
        return "floor=" + currentFloor + ", direction=" + direction + ", loadKg=" + loadKg;
    }
}

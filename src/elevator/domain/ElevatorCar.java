package elevator.domain;

import elevator.component.Display;
import elevator.component.Door;
import elevator.model.Direction;
import elevator.model.ElevatorState;
import elevator.model.HallRequest;
import elevator.security.SecurityService;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Owns one car's mutable state. Ordered stop sets serve the current direction before reversing. */
public final class ElevatorCar {
    private final int id, maxFloor, maxLoadKg;
    private final Door door = new Door();
    private final Display display = new Display();
    private final SecurityService securityService;
    private final NavigableSet<Integer> upStops = new TreeSet<>();
    private final NavigableSet<Integer> downStops = new TreeSet<>(Comparator.reverseOrder());
    private int currentFloor, currentLoadKg;
    private Direction direction = Direction.NONE;
    private ElevatorState state = ElevatorState.IDLE;

    public ElevatorCar(int id, int maxFloor, int maxLoadKg, SecurityService securityService) {
        this.id = id; this.maxFloor = maxFloor; this.maxLoadKg = maxLoadKg; this.securityService = securityService;
        updateDisplay();
    }

    public synchronized int id() { return id; }
    public synchronized int currentFloor() { return currentFloor; }
    public synchronized Direction direction() { return direction; }
    public synchronized ElevatorState state() { return state; }

    public synchronized boolean canAccept(HallRequest request) {
        return state != ElevatorState.MAINTENANCE && state != ElevatorState.EMERGENCY_STOP && currentLoadKg < maxLoadKg;
    }

    public synchronized void addPickupStop(int floor) { addStop(floor); }
    public synchronized void addDestinationStop(int floor) { addStop(floor); }

    private void addStop(int floor) {
        validateFloor(floor);
        if (state == ElevatorState.MAINTENANCE || state == ElevatorState.EMERGENCY_STOP) throw new IllegalStateException("Elevator " + id + " is unavailable");
        if (floor > currentFloor) upStops.add(floor);
        else if (floor < currentFloor) downStops.add(floor);
        else serviceCurrentFloor();
    }

    /** Executes one planned stop; a real system invokes this from the car's command/event loop. */
    public synchronized void processNextStop() {
        if (state == ElevatorState.MAINTENANCE || state == ElevatorState.EMERGENCY_STOP || isOverloaded()) return;
        Integer next = nextStop();
        if (next == null) { becomeIdle(); return; }
        if (door.isOpen()) door.close();
        direction = next > currentFloor ? Direction.UP : Direction.DOWN;
        state = direction == Direction.UP ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
        currentFloor = next; // Motor and position sensors perform this in production.
        removeStop(next);
        serviceCurrentFloor();
    }

    private Integer nextStop() {
        if (direction == Direction.UP && !upStops.isEmpty()) return upStops.first();
        if (direction == Direction.DOWN && !downStops.isEmpty()) return downStops.first();
        if (!upStops.isEmpty()) return upStops.first();
        return downStops.isEmpty() ? null : downStops.first();
    }

    private void removeStop(int floor) { upStops.remove(floor); downStops.remove(floor); }
    private void serviceCurrentFloor() { removeStop(currentFloor); becomeIdle(); door.open(); updateDisplay(); }
    private void becomeIdle() { state = ElevatorState.IDLE; direction = Direction.NONE; updateDisplay(); }
    /** The open-door button is accepted only when the car is already stationary. */
    public synchronized void openDoorIfStationary() {
        if (state == ElevatorState.IDLE) door.open();
    }
    public synchronized void closeDoor() { door.close(); }

    public synchronized void setCurrentLoadKg(int loadKg) {
        if (loadKg < 0) throw new IllegalArgumentException("Load cannot be negative");
        currentLoadKg = loadKg; updateDisplay();
    }
    public synchronized boolean isOverloaded() { return currentLoadKg > maxLoadKg; }

    public synchronized void setMaintenance(boolean enabled) {
        if (enabled) { state = ElevatorState.MAINTENANCE; direction = Direction.NONE; door.close(); }
        else if (state == ElevatorState.MAINTENANCE) becomeIdle();
        updateDisplay();
    }

    public synchronized void emergencyStop() {
        state = ElevatorState.EMERGENCY_STOP; direction = Direction.NONE; door.close(); updateDisplay();
        securityService.alertEmergency(id, currentFloor);
    }

    public synchronized String status() {
        return "ElevatorCar{id=" + id + ", state=" + state + ", " + display.read() + ", door=" + door.state() + ", queuedStops=" + (upStops.size() + downStops.size()) + "}";
    }
    private void validateFloor(int floor) { if (floor < 0 || floor > maxFloor) throw new IllegalArgumentException("Invalid floor: " + floor); }
    private void updateDisplay() { display.update(currentFloor, direction, currentLoadKg); }
}

package elevator.component;
import elevator.model.DoorState;
/** Isolated safety boundary: a car must never move while its door is open. */
public final class Door {
    private DoorState state = DoorState.CLOSED;
    public synchronized void open() { state = DoorState.OPEN; }
    public synchronized void close() { state = DoorState.CLOSED; }
    public synchronized boolean isOpen() { return state == DoorState.OPEN; }
    public synchronized DoorState state() { return state; }
}

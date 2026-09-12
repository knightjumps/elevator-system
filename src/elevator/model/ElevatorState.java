package elevator.model;
/** Maintenance and emergency cars must never be dispatched. */
public enum ElevatorState { IDLE, MOVING_UP, MOVING_DOWN, MAINTENANCE, EMERGENCY_STOP }

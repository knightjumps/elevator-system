package elevator.security;

/**
 * Replace this port with an alarm/security integration in production.
 */
public interface SecurityService {
    void alertEmergency(int elevatorId, int floor);
}

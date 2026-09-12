package elevator.security;
public final class ConsoleSecurityService implements SecurityService {
    public void alertEmergency(int elevatorId, int floor) { System.out.printf("SECURITY ALERT: elevator %d stopped at floor %d%n", elevatorId, floor); }
}

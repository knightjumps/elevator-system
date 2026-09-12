package elevator.model;
import java.time.Instant;
/** A passenger outside knows pickup floor and direction, not destination. */
public record HallRequest(int floor, Direction direction, Instant createdAt) {
    public HallRequest {
        if (floor < 0) throw new IllegalArgumentException("Floor cannot be negative");
        if (direction == null || direction == Direction.NONE) throw new IllegalArgumentException("A hall request must be UP or DOWN");
        if (createdAt == null) createdAt = Instant.now();
    }
}

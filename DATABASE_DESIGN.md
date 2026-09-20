# Database Design for the Elevator System

This document explains how to discuss persistence when an interviewer asks for the database design of an elevator system.

The most important design decision is that the database must not be part of the safety-critical movement loop. An elevator's local controller must continue to operate safely if the network or database is unavailable. The database supports configuration, monitoring, recovery, auditing, and maintenance; it does not decide whether the motor may move or whether a door may open.

> The real-time car controller is authoritative for physical operation. Database state is a durable, last-known representation used by the wider system.

## 1. Clarify the persistence requirements

Before proposing tables, ask:

- Is this only an in-memory LLD simulation?
- Must pending requests survive a controller restart?
- Do we need historical trip and telemetry data?
- Do maintenance teams need fault and service history?
- Are we designing one building or a fleet of buildings?
- How long must telemetry and event history be retained?

For a small interview simulation, the core system needs no database. Cars, stop queues, and requests can remain in memory. The schema below becomes relevant when durability, monitoring, recovery, and operational reporting are required.

## 2. In-memory state versus persisted state

| Data | Preferred location | Reason |
|---|---|---|
| Current physical floor | Local controller memory | Changes frequently and is required immediately |
| Current direction | Local controller memory | Part of real-time movement control |
| Door state | Local controller memory | Safety-critical |
| Active stop queues | Local memory, optionally checkpointed | Needed for real-time scheduling |
| Building configuration | Relational database and local cache | Durable and changes infrequently |
| Elevator configuration | Relational database and local cache | Durable and changes infrequently |
| Last-known car state | Snapshot table | Monitoring and restart assistance |
| Requests and assignments | Transactional database/event log | Auditing, analytics, and recovery |
| Faults and emergencies | Transactional database/event log | Investigation and maintenance |
| Maintenance history | Relational database | Long-lived operational data |
| High-frequency sensor telemetry | Time-series database | High volume and time-window queries |

The database's current-floor value is only the last reported floor. If it disagrees with a physical position sensor after recovery, the sensor wins.

## 3. Storage choices

A relational database such as PostgreSQL is a good starting point because buildings, floors, elevator cars, requests, faults, and maintenance records have well-defined relationships and integrity constraints.

For a large fleet:

- Keep configuration and transactional records in PostgreSQL or another relational database.
- Send operational events through a durable stream such as Kafka.
- Store high-frequency metrics in a time-series database.
- Archive old event data to cheaper object storage if long retention is required.

These additional systems are not necessary for a small single-building implementation.

## 4. Core configuration schema

### `building`

Stores building-level configuration.

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT | Primary key |
| `name` | VARCHAR | Human-readable building name |
| `address` | VARCHAR | Optional physical location |
| `timezone` | VARCHAR | Reporting and local-time conversion |
| `status` | VARCHAR | `ACTIVE`, `MAINTENANCE`, or `EVACUATION` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### `floor`

Stores the valid floors within each building.

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT | Primary key |
| `building_id` | Foreign key | Owning building |
| `floor_number` | INT | Internal numeric floor representation |
| `display_name` | VARCHAR | User-facing label such as `G`, `B1`, or `12` |
| `is_serviceable` | BOOLEAN | Whether elevators may currently serve it |

Recommended constraint:

```sql
UNIQUE (building_id, floor_number)
```

Floor numbers should not be assumed to be positive. Basements can be represented by negative values.

### `elevator_car`

Stores stable configuration for each car.

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID or BIGINT | Primary key |
| `building_id` | Foreign key | Building containing the car |
| `car_number` | VARCHAR | Human-readable identifier |
| `max_load_kg` | INT | Maximum safe load |
| `max_passengers` | INT | Optional passenger-count limit |
| `min_floor` | INT | Lowest serviceable floor for simple configurations |
| `max_floor` | INT | Highest serviceable floor for simple configurations |
| `operational_status` | VARCHAR | `ACTIVE`, `MAINTENANCE`, or `OUT_OF_SERVICE` |
| `installed_at` | TIMESTAMP | Installation and maintenance information |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### `elevator_serviceable_floor`

Use this mapping table when cars do not all serve the same contiguous range—for example, an express elevator or a car that cannot enter the basement.

| Column | Type | Purpose |
|---|---|---|
| `elevator_id` | Foreign key | Elevator car |
| `floor_id` | Foreign key | Floor served by the car |

Recommended key:

```sql
PRIMARY KEY (elevator_id, floor_id)
```

## 5. Current-state snapshot

### `elevator_state`

Stores one latest-known snapshot per car for dashboards, health checks, and restart assistance.

| Column | Type | Purpose |
|---|---|---|
| `elevator_id` | Foreign key and primary key | One snapshot per car |
| `current_floor` | INT | Last reported physical floor |
| `direction` | VARCHAR | `UP`, `DOWN`, or `NONE` |
| `movement_state` | VARCHAR | `IDLE`, `MOVING_UP`, `MOVING_DOWN`, etc. |
| `door_state` | VARCHAR | `OPEN`, `CLOSED`, `OPENING`, or `CLOSING` |
| `current_load_kg` | INT | Latest aggregate sensor measurement |
| `is_overloaded` | BOOLEAN | Derived or cached overload status |
| `target_floor` | INT, nullable | Immediate target floor |
| `last_heartbeat_at` | TIMESTAMP | Detects disconnected controllers |
| `version` | BIGINT | Prevents stale updates from overwriting newer state |
| `updated_at` | TIMESTAMP | Last snapshot update |

The table need not be updated for every motor pulse. Useful update points include:

- reaching a floor;
- changing direction;
- opening or closing doors;
- detecting overload or a fault;
- sending a periodic heartbeat.

The `version` or controller sequence number protects against out-of-order updates. For example, version 104 must not overwrite version 105 just because it arrived later over the network.

## 6. Request persistence

Hall and cabin requests carry different information:

- A hall request has a pickup floor and an intended direction.
- A cabin request belongs to a selected elevator and has a destination floor.

### Option A: Generalized `elevator_request`

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Globally unique, idempotent request ID |
| `building_id` | Foreign key | Building receiving the request |
| `request_type` | VARCHAR | `HALL` or `CABIN` |
| `source_floor` | INT, nullable | Required for a hall request |
| `direction` | VARCHAR, nullable | `UP` or `DOWN` for a hall request |
| `destination_floor` | INT, nullable | Required for a cabin request |
| `assigned_elevator_id` | Foreign key, nullable | Car chosen by the dispatcher |
| `status` | VARCHAR | Request lifecycle state |
| `requested_at` | TIMESTAMP | Creation time |
| `assigned_at` | TIMESTAMP, nullable | Dispatch time |
| `served_at` | TIMESTAMP, nullable | Completion time |
| `cancelled_at` | TIMESTAMP, nullable | Cancellation time |
| `failure_reason` | VARCHAR, nullable | Failure explanation |

Example lifecycle:

```text
PENDING -> ASSIGNED -> IN_PROGRESS -> SERVED
                         |
                         +----------> FAILED
```

Useful indexes:

```sql
CREATE INDEX idx_request_pending
    ON elevator_request (building_id, status, requested_at);

CREATE INDEX idx_request_by_car
    ON elevator_request (assigned_elevator_id, status);
```

This approach is convenient for querying all requests, but some columns are necessarily nullable.

### Option B: Separate request tables

Use `hall_request` and `cabin_request` separately when the two request types develop significantly different behavior or validation rules. This provides stronger schemas and fewer nullable columns, but cross-request queries become more complicated.

For an interview, either approach is reasonable if the trade-off is explained. A generalized table is a practical starting point.

## 7. Recoverable stop queues

### `elevator_stop`

Use this table only if queued stops must survive a local controller restart.

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Stop identifier |
| `elevator_id` | Foreign key | Assigned car |
| `floor_number` | INT | Target floor |
| `direction` | VARCHAR | Queue direction |
| `request_id` | Foreign key, nullable | Request that created the stop |
| `sequence_number` | BIGINT | Ordering within the car |
| `status` | VARCHAR | `QUEUED`, `ARRIVED`, `SERVED`, or `CANCELLED` |
| `created_at` | TIMESTAMP | Queue time |
| `served_at` | TIMESTAMP, nullable | Completion time |

The controller should load the queue into memory and process it locally. It should not query this table for every movement tick.

## 8. Append-only event history

### `elevator_event`

A state snapshot answers “Where is the car now?” An event history answers “How did it get here, and why did it fail?”

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Event identifier |
| `building_id` | Foreign key | Building |
| `elevator_id` | Foreign key, nullable | Related car |
| `event_type` | VARCHAR | Event category |
| `floor_number` | INT, nullable | Relevant floor |
| `payload` | JSON or JSONB | Event-specific metadata |
| `occurred_at` | TIMESTAMP | Time generated by the controller |
| `recorded_at` | TIMESTAMP | Time received by persistence |
| `sequence_number` | BIGINT | Ordering per elevator |

Example event types:

```text
HALL_REQUESTED
REQUEST_ASSIGNED
MOVEMENT_STARTED
FLOOR_REACHED
DOOR_OPENED
DOOR_CLOSED
OVERLOAD_DETECTED
EMERGENCY_STOPPED
FAULT_DETECTED
MAINTENANCE_STARTED
```

Keeping both snapshot and event storage is useful:

```text
elevator_state -> fast current-state lookup
elevator_event -> immutable history and incident investigation
```

For high event volume, events can first be published to a stream and persisted asynchronously.

## 9. Faults and maintenance

### `fault`

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Fault identifier |
| `elevator_id` | Foreign key | Affected car |
| `fault_code` | VARCHAR | Machine-readable fault category |
| `severity` | VARCHAR | Warning, critical, etc. |
| `description` | TEXT | Human-readable information |
| `status` | VARCHAR | `OPEN`, `ACKNOWLEDGED`, or `RESOLVED` |
| `detected_at` | TIMESTAMP | Detection time |
| `resolved_at` | TIMESTAMP, nullable | Resolution time |

### `maintenance_record`

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Maintenance record ID |
| `elevator_id` | Foreign key | Serviced car |
| `maintenance_type` | VARCHAR | Preventive, corrective, inspection, etc. |
| `technician_id` | Foreign key or VARCHAR, nullable | Responsible technician |
| `description` | TEXT | Work performed |
| `started_at` | TIMESTAMP | Start time |
| `completed_at` | TIMESTAMP, nullable | Completion time |
| `next_service_at` | TIMESTAMP, nullable | Future scheduled service |
| `status` | VARCHAR | Scheduled, in progress, or completed |

These tables hold long-lived operational records and should be durable.

## 10. Passenger and load data

A real elevator normally should not persist individual passenger identities or weights. That creates unnecessary privacy concerns and provides little operational value.

The elevator should report an aggregate sensor reading:

```text
current_load_kg = 426
```

For an educational simulation, `Passenger` objects may exist in memory to demonstrate boarding and exiting. They generally do not need database rows.

## 11. Runtime write flow

```text
Passenger presses hall button
        |
        v
Local controller creates an in-memory request
        |
        v
Dispatcher assigns an elevator
        |
        v
Request/assignment is persisted asynchronously
        |
        v
Elevator operates locally without waiting for the database
        |
        v
Important state transitions and events are persisted
```

If the database is unavailable:

- elevators continue operating safely;
- important events are buffered locally;
- buffered events are retried using idempotent IDs;
- monitoring may temporarily show stale state;
- emergency operation remains completely local.

## 12. Restart and recovery

When a local controller restarts:

1. Load durable building and car configuration.
2. Load the latest state snapshot and optional pending-stop checkpoint.
3. Read actual floor, door, load, and fault state from physical sensors.
4. Reconcile persisted state with those readings.
5. Treat physical sensors as authoritative when they disagree.
6. Safely rebuild or resume pending work.

The database must never force a car to assume it is at floor 7 when its physical sensor reports floor 6.

## 13. Transactions, concurrency, and idempotency

Important guarantees include:

- Give every request a unique idempotency ID.
- Atomically assign a request and record its assignment.
- Prevent two dispatchers from assigning the same request.
- Use an optimistic-locking `version` or conditional database update.
- Partition operational work by `building_id`.
- Order car events with a per-elevator sequence number, not timestamps alone.
- Avoid one global lock covering every elevator.

Example conditional assignment:

```sql
UPDATE elevator_request
SET status = 'ASSIGNED',
    assigned_elevator_id = ?,
    assigned_at = CURRENT_TIMESTAMP
WHERE id = ?
  AND status = 'PENDING';
```

If the affected-row count is zero, another dispatcher has already assigned or changed the request.

Within the same transaction, insert the corresponding `REQUEST_ASSIGNED` event. This keeps the request state and audit history consistent.

## 14. Interview-ready answer

> For a basic LLD, I would keep movement state and active queues in memory, so no database is required. If persistence is required, I would use a relational database for buildings, floors, elevator configuration, requests, faults, and maintenance records. I would maintain a last-known-state snapshot per elevator and an append-only event history for auditing. The local controller would never synchronously depend on the database for movement or door safety. High-volume sensor telemetry would go to a time-series store, while request assignment would use transactions, idempotent request IDs, and optimistic locking to prevent duplicate assignment.

The principal relationships are:

```text
building
   |-- floor
   `-- elevator_car
          |-- elevator_state       (latest snapshot)
          |-- elevator_stop        (recoverable queue)
          |-- elevator_event       (history)
          |-- fault
          `-- maintenance_record

elevator_request -- assigned_elevator_id --> elevator_car
```

## 15. Common mistakes

- Putting `currentFloor++` behind a synchronous database call.
- Treating persisted state as more authoritative than physical sensors.
- Writing high-frequency raw telemetry into the main transactional database.
- Persisting personally identifiable passenger data unnecessarily.
- Keeping only mutable state and losing incident history.
- Keeping only events when dashboards require fast current-state lookup.
- Forgetting idempotency and allowing one request to be assigned twice.
- Persisting every simulation-loop iteration rather than meaningful transitions.
- Introducing Kafka, a time-series database, and multiple services before the requirements justify them.

## 16. Key takeaways

- The real-time controller must operate without the database.
- Persist configuration, requests, snapshots, events, faults, and maintenance history.
- Keep both current-state snapshots and append-only event history.
- Treat physical sensors as authoritative for physical state.
- Use transactions, idempotency, and versioning to prevent duplicate or stale updates.
- Keep high-volume telemetry separate from transactional business data.

## 17. Consolidated table catalog

| Table | Required or optional | Primary use case |
|---|---|---|
| `building` | Required for persistent multi-building configuration | Stores building identity, location, timezone, and operational status |
| `floor` | Required | Defines valid floors and their user-facing labels for each building |
| `elevator_car` | Required | Stores durable car configuration, capacity, range, and operational availability |
| `elevator_serviceable_floor` | Optional | Models express cars, restricted floors, and non-contiguous service ranges |
| `elevator_state` | Recommended | Provides the latest known car state for dashboards, heartbeats, and recovery assistance |
| `elevator_request` | Recommended when requests require durability | Tracks hall/cabin requests, assignment, lifecycle, completion, and failure |
| `hall_request` | Alternative to generalized request table | Stores strongly typed hall calls when hall and cabin workflows are separated |
| `cabin_request` | Alternative to generalized request table | Stores strongly typed in-car destination selections |
| `elevator_stop` | Optional | Checkpoints queued stops so a controller can recover them after restart |
| `elevator_event` | Recommended | Stores append-only operational and safety history for auditing and incident analysis |
| `fault` | Recommended for production | Tracks detected faults, severity, acknowledgement, and resolution |
| `maintenance_record` | Recommended for production | Stores inspections, repairs, technician work, and future service schedules |
| Time-series telemetry store | Optional at scale | Stores high-frequency sensor measurements without overloading the transactional database |

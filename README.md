# Elevator System — Low-Level Design (Java)

An in-memory Java implementation of a multi-elevator control system. The project is designed as an LLD exercise: it emphasizes clear responsibilities, safe state transitions, and replaceable dispatch policies rather than hardware integration.

## What it models

- A configurable building with floors and multiple elevator cars
- Hall requests: a passenger requests an elevator in an `UP` or `DOWN` direction
- Cabin requests: a passenger selects a destination after entering a car
- Direction-aware scheduling through a pluggable dispatch strategy
- Ordered upward and downward stop queues, which prevent duplicate stops
- Car states: idle, moving up, moving down, maintenance, and emergency stop
- Door and display state
- Capacity/overload checks, maintenance mode, and emergency security alerts

Each call to `ElevatorCar.processNextStop()` is one **simulation tick**. A car moves only one physical floor per tick; when it reaches a queued stop, it becomes idle and opens its door.

```text
floor 0, next stop 3
  tick 1 -> floor 1
  tick 2 -> floor 2
  tick 3 -> floor 3, open door, serve stop
```

## Design overview

```text
HallPanel ──> ElevatorController ──> SchedulingStrategy ──> ElevatorCar
                                                            ├── Door
                                                            ├── Display
                                                            └── ordered stop queues

ElevatorPanel ────────────────────────────────────────────> ElevatorCar
```

`ElevatorController` coordinates requests shared by all cars. An `ElevatorCar` owns its own movement, state, and queued stops. This separation prevents a controller from directly changing a car's internal state.

## Package structure

```text
src/
├── Main.java                    # Runnable demonstration
└── elevator/
    ├── component/               # Door and display
    ├── dispatch/                # Controller and dispatch strategies
    ├── domain/                  # ElevatorCar and Floor
    ├── model/                   # States, directions, and request value objects
    ├── panel/                   # Hall and in-car panels
    ├── security/                # Emergency alert abstraction
    └── system/                  # Building composition root
```

## Important design decisions

### Hall versus cabin requests

A hall request contains a pickup floor and passenger direction. A cabin request belongs to one specific elevator car and contains a destination floor. They are intentionally handled differently because only hall requests need global dispatching.

### Direction-aware stop queues

`ElevatorCar` stores upward and downward stops separately. It favors stops in its current travel direction before reversing. This resembles the elevator/SCAN scheduling algorithm used in disk scheduling.

### Strategy pattern for dispatching

`SchedulingStrategy` allows dispatch rules to vary without rewriting `ElevatorController`. The included `NearestEligibleCarStrategy` prefers eligible cars already travelling toward the request; a peak-hour or destination-dispatch strategy can be added later.

### Safety rules

- A car in `MAINTENANCE` or `EMERGENCY_STOP` cannot accept requests.
- A moving car closes an open door before moving.
- The open-door button is honored only while the car is stationary.
- An overloaded car does not process another movement tick.

## Run the demo

Requires JDK 17 or newer.

```bash
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out Main
```

The demo creates a 12-floor building with three elevators, submits hall/cabin requests, advances the simulation for several ticks, puts one car into maintenance mode, and prints each car's status.

## Current scope and extensions

This is an LLD simulation, not a physical elevator controller. In a production implementation, motor control, floor sensors, obstruction detection, timed door closing, and emergency/fire policies would be hardware- and regulation-specific adapters.

Useful next enhancements:

- Model passengers so weight is added on boarding and removed on exit
- Add configurable automatic door-close timing
- Run each car on its own command queue/event loop for concurrent requests
- Persist requests and telemetry for recovery and monitoring
- Add alternate scheduling strategies for peak traffic and destination dispatch

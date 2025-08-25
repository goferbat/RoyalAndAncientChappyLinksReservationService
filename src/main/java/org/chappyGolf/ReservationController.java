package org.chappyGolf;

import org.chappyGolf.dto.CreateReservationRequest;
import org.chappyGolf.dto.Reservation;
import org.chappyGolf.dto.TeeTimeStatus;
import org.chappyGolf.services.ReservationService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(@RequestBody CreateReservationRequest req) {
        if (req.name() == null || req.name().isBlank() || req.time() == null)
            return Map.of("ok", false, "message", "Provide 'name' and 'time'.");
        try {
            var saved = service.reserveOne(req.name().trim(), req.time().trim(), req.numOfPlayers());
            return Map.of("ok", true, "id", saved.id(), "name", saved.name(), "time", saved.time());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @DeleteMapping("/reservations/{id}")
    public Map<String, Object> cancel(@PathVariable String id) {
        var removed = service.cancelById(id);
        return (removed != null)
                ? Map.of("ok", true, "message", "Cancelled",
                "id", id, "time", removed.time(), "freedPlayers", removed.numOfPlayers())
                : Map.of("ok", false, "message", "Reservation not found", "id", id);
    }

    @GetMapping("/tee-times")
    public List<String> available() {
        return service.getTimesWithAvailability();
    }
    @GetMapping("/tee-times/status")
    public List<TeeTimeStatus> status() {
        return service.getStatus();
    }
    @GetMapping("/reservations")
    public Map<String, List<Reservation>> all() {
        return service.getAllReservations();
    }
}
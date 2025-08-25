package org.chappyGolf;

import org.chappyGolf.dto.CreateReservationRequest;
import org.chappyGolf.dto.Reservation;
import org.chappyGolf.dto.TeeTimeStatus;
import org.chappyGolf.services.MailService;
import org.chappyGolf.services.ReservationService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ReservationController {

    private final ReservationService service;
    private final MailService mail;

    public ReservationController(ReservationService service, MailService mail) {
        this.service = service;
        this.mail = mail;
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(@RequestBody CreateReservationRequest req) {
        if (req.name() == null || req.name().isBlank() || req.time() == null || req.email() == null || req.email().isBlank()) {
            return Map.of("ok", false, "message", "Provide 'name', 'time', and 'email'.");
        }
        try {
            var saved = service.reserveOne(req.name().trim(), req.time().trim());
            // fire-and-forget email
            mail.sendReservationEmail(req.email().trim(), saved);
            return Map.of("ok", true, "id", saved.id(), "name", saved.name(), "time", saved.time());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @DeleteMapping("/reservations/{id}")
    public Map<String, Object> cancel(@PathVariable String id, @RequestParam(required=false) String email) {
        // optional: look up the original reservation to email the same address
        var res = service.findById(id); // add this helper in service (shown below)
        boolean removed = service.cancelById(id);
        if (removed && res != null && email != null && !email.isBlank()) {
            mail.sendCancellationEmail(email.trim(), res);
        }
        return removed
                ? Map.of("ok", true, "message", "Cancelled", "id", id)
                : Map.of("ok", false, "message", "Reservation not found", "id", id);
    }
}
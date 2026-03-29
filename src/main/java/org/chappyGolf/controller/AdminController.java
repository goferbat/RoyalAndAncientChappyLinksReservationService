package org.chappyGolf.controller;

import com.squareup.square.core.SquareApiException;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.dto.AdminReservationDetailResponse;
import org.chappyGolf.dto.ReservationStatusResponse;
import org.chappyGolf.dto.TeeSheetReservationDto;
import org.chappyGolf.model.cayenne.Payment;
import org.chappyGolf.model.cayenne.Reservation;
import org.chappyGolf.service.TeeTimeSeedService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ObjectContext context;
    private final TeeTimeSeedService teeTimeSeedService;
    private final GolfController golfController;

    public AdminController(ObjectContext context,
                           TeeTimeSeedService teeTimeSeedService,
                           GolfController golfController) {
        this.context = context;
        this.teeTimeSeedService = teeTimeSeedService;
        this.golfController = golfController;
    }

    @GetMapping("/tee-sheet")
    public List<TeeSheetReservationDto> getTeeSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");

        List<Reservation> reservations = ObjectSelect.query(Reservation.class)
                .select(context);

        return reservations.stream()
                .filter(r -> {
                    LocalDateTime teeTime = r.getTeeTime().getStartTime();
                    return !teeTime.isBefore(start) && teeTime.isBefore(end);
                })
                .map(r -> {
                    Payment payment = r.getPayments().isEmpty() ? null : r.getPayments().get(0);

                    LocalDateTime startTime = r.getTeeTime().getStartTime();

                    String slotLabel = startTime
                            .atZone(ZoneId.of("America/New_York"))
                            .format(formatter);

                    return new TeeSheetReservationDto(
                            (Integer) r.getObjectId().getIdSnapshot().get("id"),
                            (Integer) r.getTeeTime().getObjectId().getIdSnapshot().get("id"),
                            r.getUser().getName(),
                            r.getUser().getEmail(),
                            r.getPartySize(),
                            startTime,
                            slotLabel,
                            r.getTier().getName(),
                            payment != null ? payment.getAmountCents() : 0,
                            payment != null ? payment.getStatus() : "UNKNOWN"
                    );
                })
                .sorted(Comparator.comparing(TeeSheetReservationDto::getStartTime))
                .toList();
    }

    @GetMapping("/reservations/{id}")
    public AdminReservationDetailResponse getReservation(@PathVariable int id) {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) {
            throw new RuntimeException("Reservation not found");
        }

        Payment payment = reservation.getPayments().isEmpty() ? null : reservation.getPayments().get(0);

        return new AdminReservationDetailResponse(
                id,
                (Integer) reservation.getUser().getObjectId().getIdSnapshot().get("id"),
                reservation.getUser().getName(),
                reservation.getUser().getEmail(),
                (Integer) reservation.getTeeTime().getObjectId().getIdSnapshot().get("id"),
                reservation.getTeeTime().getStartTime(),
                reservation.getTier().getName(),
                reservation.getPartySize(),
                payment != null ? payment.getAmountCents() : 0,
                payment != null ? payment.getStatus() : "UNKNOWN",
                payment != null ? payment.getSquarePaymentId() : null
        );
    }

    @PostMapping("/reservations/{id}/capture")
    public ReservationStatusResponse capture(@PathVariable int id) throws SquareApiException {
        return golfController.captureReservationPayment(id);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationStatusResponse cancel(@PathVariable int id) throws SquareApiException {
        golfController.cancelReservationPayment(id);
        return golfController.getReservationStatus(id);
    }

    @PostMapping("/reservations/{id}/refund")
    public ReservationStatusResponse refund(@PathVariable int id) throws SquareApiException {
        golfController.refundReservation(id);
        return golfController.getReservationStatus(id);
    }

    @PostMapping("/tee-times/seed/{date}")
    public String seedTeeTimes(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        teeTimeSeedService.seedForDate(date);
        return "Seeded tee times for " + date;
    }
}
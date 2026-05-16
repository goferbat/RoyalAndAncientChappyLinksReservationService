package org.chappyGolf.controller;

import com.squareup.square.core.SquareApiException;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.dto.AdminReservationDetailResponse;
import org.chappyGolf.dto.AdminTeeTimeDto;
import org.chappyGolf.dto.AdminTeeTimeReservationDto;
import org.chappyGolf.dto.ReservationStatusResponse;
import org.chappyGolf.dto.UpdateTeeTimeBlockRequest;
import org.chappyGolf.model.cayenne.Payment;
import org.chappyGolf.model.cayenne.Reservation;
import org.chappyGolf.model.cayenne.TeeTime;
import org.chappyGolf.services.TeeTimeSeedService;
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
    public List<AdminTeeTimeDto> getTeeSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");

        List<TeeTime> teeTimes = ObjectSelect.query(TeeTime.class)
                .select(context);

        return teeTimes.stream()
                .filter(t -> {
                    LocalDateTime teeTimeStart = t.getStartTime();
                    return !teeTimeStart.isBefore(start) && teeTimeStart.isBefore(end);
                })
                .sorted(Comparator.comparing(TeeTime::getStartTime))
                .map(t -> {
                    List<AdminTeeTimeReservationDto> reservations = t.getReservations().stream()
                            .map(r -> {
                                Payment payment = r.getPayments().isEmpty() ? null : r.getPayments().get(0);

                                return new AdminTeeTimeReservationDto(
                                        (Integer) r.getObjectId().getIdSnapshot().get("id"),
                                        r.getUser().getName(),
                                        r.getUser().getEmail(),
                                        r.getPartySize(),
                                        r.getTier().getName(),
                                        payment != null ? payment.getAmountCents() : 0,
                                        payment != null ? payment.getStatus() : "UNKNOWN",
                                        r.isTransportation()
                                );
                            })
                            .sorted(Comparator.comparing(AdminTeeTimeReservationDto::customerName,
                                    Comparator.nullsLast(String::compareToIgnoreCase)))
                            .toList();

                    int reservedSpots = t.getReservations().stream()
                            .mapToInt(Reservation::getPartySize)
                            .sum();

                    String slotLabel = t.getStartTime()
                            .atZone(ZoneId.of("America/New_York"))
                            .format(formatter);

                    Integer teeTimeId = (Integer) t.getObjectId().getIdSnapshot().get("id");
                    int capacity = t.getCapacity() != null ? t.getCapacity() : 0;
                    int spotsRemaining = Math.max(0, capacity - reservedSpots);

                    return new AdminTeeTimeDto(
                            teeTimeId,
                            t.getStartTime(),
                            slotLabel,
                            capacity,
                            spotsRemaining,
                            t.isBlocked(),
                            t.getBlockedReason(),
                            reservations
                    );
                })
                .toList();
    }

    @PutMapping("/tee-times/{id}/block")
    public String updateTeeTimeBlock(
            @PathVariable int id,
            @RequestBody UpdateTeeTimeBlockRequest request) {

        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, id);
        if (teeTime == null) {
            throw new RuntimeException("Tee time not found");
        }

        teeTime.setBlocked(request.blocked());
        teeTime.setBlockedReason(request.blocked()
                ? (request.blockedReason() == null ? "" : request.blockedReason().trim())
                : null);

        context.commitChanges();

        return request.blocked()
                ? "Tee time blocked successfully"
                : "Tee time unblocked successfully";
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
                reservation.isTransportation(),
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

    @PostMapping("/reservations/{id}/no-show")
    public ReservationStatusResponse noShow(@PathVariable int id) throws SquareApiException {
        return golfController.noShowReservation(id);
    }

}
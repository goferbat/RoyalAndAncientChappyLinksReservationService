package org.chappyGolf.controller;

import com.squareup.square.SquareClient;
import com.squareup.square.core.SquareApiException;
import com.squareup.square.types.*;
import org.chappyGolf.dto.*;
import org.chappyGolf.model.cayenne.*;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.model.cayenne.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GolfController {

    private final ObjectContext context;
    private final SquareClient squareClient;
    private final String squareLocationId;

    public GolfController(ObjectContext context,
                          SquareClient squareClient,
                          @Value("${square.location.id}") String squareLocationId) {
        this.context = context;
        this.squareClient = squareClient;
        this.squareLocationId = squareLocationId;
    }

    // ---- Users ----
    @PostMapping("/users")
    public void createUser(@RequestBody UserDto dto) {
        Users user = context.newObject(Users.class);
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        context.commitChanges();
    }

    // ---- Tee Times ----
    @GetMapping("/tee-times")
    public List<TeeTimeResponse> listTeeTimes() {
        // Load all tee times
        List<TeeTime> teeTimes = ObjectSelect.query(TeeTime.class).select(context);

        // Load tiers once (since they're always the same for every tee time)
        List<TeeTimeTier> tiers = ObjectSelect.query(TeeTimeTier.class).select(context);

        return teeTimes.stream()
                .map(tt -> new TeeTimeResponse(
                        Cayenne.intPKForObject(tt),
                        tt.getStartTime(),
                        tt.getCapacity(),
                        tiers.stream()
                                .map(t -> new TeeTimeTierDto(Cayenne.intPKForObject(t), t.getName(), t.getPriceCents()))
                                .toList()
                ))
                .toList();
    }

    @PostMapping("/tee-times/post")
    public TeeTime createTeeTime(@RequestBody TeeTimeDto dto) {
        TeeTime teeTime = context.newObject(TeeTime.class);
        teeTime.setStartTime(dto.getSlot());
        teeTime.setCapacity(dto.getCapacity());
        context.commitChanges();
        return teeTime;
    }

    @PostMapping("/tee-times/seed")
    public void seedTeeTimes() {
        // Create slots from 9:00 AM to 5:00 PM
        for (int hour = 9; hour <= 17; hour++) {
            TeeTime teeTime = context.newObject(TeeTime.class);
            teeTime.setStartTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, 0)));
            teeTime.setCapacity(8);
        }
        context.commitChanges();
    }

    @GetMapping("/reservations/{id}/status")
    public ReservationStatusResponse getReservationStatus(@PathVariable int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        // ✅ Build the request with the paymentId
        GetPaymentsRequest req = GetPaymentsRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();

        var resp = squareClient.payments().get(req);
        var sqPayment = resp.getPayment().orElseThrow();

        String status = sqPayment.getStatus().orElse("UNKNOWN");

        // keep DB in sync with Square
        payment.setStatus(status);
        context.commitChanges();

        return new ReservationStatusResponse(
                Cayenne.intPKForObject(reservation),
                payment.getStatus(),
                reservation.getPartySize(),
                reservation.getTeeTime().getStartTime()
        );
    }

    @PostMapping("/reservations")
    public Reservation reserve(@RequestBody ReservationDto dto) throws SquareApiException {
        Users user = Cayenne.objectForPK(context, Users.class, dto.getUserId());
        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, dto.getTeeTimeId());
        TeeTimeTier tier = Cayenne.objectForPK(context, TeeTimeTier.class, dto.getTeeTimeTier());

        if (user == null || teeTime == null || tier == null) {
            throw new RuntimeException("Invalid booking data");
        }
        if (dto.getPartySize() < 1 || dto.getPartySize() > 6) {
            throw new RuntimeException("Party size must be between 1 and 6");
        }
        if (teeTime.getCapacity() < dto.getPartySize()) {
            throw new RuntimeException("Not enough capacity left");
        }

        long totalAmount = tier.getPriceCents() * dto.getPartySize();

        Money amount = Money.builder()
                .amount(totalAmount)
                .currency(Currency.USD)
                .build();

        CreatePaymentRequest paymentReq = CreatePaymentRequest.builder()
                .sourceId(dto.getSourceId())
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(squareLocationId)
                .autocomplete(false)  // HOLDS CARD
                .build();

        CreatePaymentResponse paymentResp = squareClient.payments().create(paymentReq);

        var sqPayment = paymentResp.getPayment()
                .orElseThrow(() -> new RuntimeException("Square payment missing"));
        String paymentId = sqPayment.getId().orElseThrow();
        String status = sqPayment.getStatus().orElse("UNKNOWN");

        if (!"AUTHORIZED".equals(status) && !"APPROVED".equals(status)) {
            throw new RuntimeException("Payment hold failed with status: " + status);
        }

        Reservation reservation = context.newObject(Reservation.class);
        reservation.setUser(user);
        reservation.setTeeTime(teeTime);
        reservation.setTier(tier);
        reservation.setPartySize(dto.getPartySize());
        reservation.setCreatedAt(LocalDateTime.now());
        teeTime.setCapacity(teeTime.getCapacity() - dto.getPartySize());

        Payment payment = context.newObject(Payment.class);
        payment.setReservation(reservation);
        payment.setSquarePaymentId(paymentId);
        payment.setAmountCents(totalAmount);
        payment.setStatus(status); // should be AUTHORIZED
        payment.setCreatedAt(LocalDateTime.now());

        context.commitChanges();
        return reservation;
    }

    @PostMapping("/reservations/{id}/capture")
    public void captureReservationPayment(@PathVariable int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        var req = CompletePaymentRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();

        var resp = squareClient.payments().complete(req); // COMPLETE PAYMENT

        var sqPayment = resp.getPayment().orElseThrow();
        String status = sqPayment.getStatus().orElse("UNKNOWN");
        if (!"COMPLETED".equals(status)) {
            throw new RuntimeException("Capture failed, status: " + status);
        }

        payment.setStatus("COMPLETED");
        context.commitChanges();
    }

    @PostMapping("/reservations/{id}/cancel-payment")
    public void cancelReservationPayment(@PathVariable int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        CancelPaymentsRequest req = CancelPaymentsRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();

        var resp = squareClient.payments().cancel(req);

        var sqPayment = resp.getPayment().orElseThrow();
        String status = sqPayment.getStatus().orElse("UNKNOWN");
        if (!"CANCELED".equals(status)) {
            throw new RuntimeException("Cancel failed, status: " + status);
        }

        // restore capacity since they never showed
        TeeTime teeTime = reservation.getTeeTime();
        teeTime.setCapacity(teeTime.getCapacity() + reservation.getPartySize());

        payment.setStatus("CANCELED");
        context.commitChanges();
    }

    @DeleteMapping("/reservations/{id}/refund")
    public void cancelReservation(@PathVariable int id) throws com.squareup.square.core.SquareApiException {
        // Look up local records
        Reservation reservation = org.apache.cayenne.Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = org.apache.cayenne.query.ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("No payment found for reservation " + id);

        TeeTime teeTime = reservation.getTeeTime();

        // Build refund request (v45)
        com.squareup.square.types.Money amt = com.squareup.square.types.Money.builder()
                .amount(payment.getAmountCents())
                .currency(com.squareup.square.types.Currency.USD)
                .build();

        System.out.println(Money.builder().amount(payment.getAmountCents()).currency(Currency.USD).build());

        RefundPaymentRequest refundReq = RefundPaymentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(Money.builder()
                        .amount(payment.getAmountCents()) // must match or be less
                        .currency(Currency.USD)
                        .build())
                .paymentId(payment.getSquarePaymentId())
                .reason("Reservation cancel")
                .build();

        var refundResp = squareClient.refunds().refundPayment(refundReq);

        var sqRefund = refundResp.getRefund()
                .orElseThrow(() -> new RuntimeException("Refund missing in Square response"));
        String refundStatus = sqRefund.getStatus().orElse("UNKNOWN");
        if (!"COMPLETED".equals(refundStatus) && !"APPROVED".equals(refundStatus) && !"PENDING".equals(refundStatus)) {
            throw new RuntimeException("Refund failed with status: " + refundStatus);
        }

        // Restore capacity and update local payment status (keep history)
        teeTime.setCapacity(teeTime.getCapacity() + reservation.getPartySize());
        payment.setStatus("REFUNDED");
        context.commitChanges();
    }
}
package org.chappyGolf.controller;

import com.squareup.square.SquareClient;
import com.squareup.square.core.SquareApiException;
import com.squareup.square.types.*;
import org.chappyGolf.model.cayenne.*;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.dto.ReservationDto;
import org.chappyGolf.dto.TeeTimeDto;
import org.chappyGolf.dto.UserDto;
import org.chappyGolf.model.cayenne.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    public List<TeeTime> listTeeTimes() {
        return ObjectSelect.query(TeeTime.class)
                .where(TeeTime.RESERVED.isFalse())
                .select(context);
    }

    @PostMapping("/tee-times/post")
    public TeeTime createTeeTime(@RequestBody TeeTimeDto dto) {
        TeeTime teeTime = context.newObject(TeeTime.class);
        teeTime.setSlot(dto.getSlot());
        teeTime.setReserved(false);
        teeTime.setCapacity(dto.getCapacity());
        context.commitChanges();
        return teeTime;
    }

    @PostMapping("/tee-times/seed")
    public void seedTeeTimes() {
        // Create slots from 9:00 AM to 5:00 PM
        for (int hour = 9; hour <= 17; hour++) {
            TeeTime teeTime = context.newObject(TeeTime.class);
            teeTime.setSlot(LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, 0)));
            teeTime.setReserved(false);
            teeTime.setCapacity(8);
        }
        context.commitChanges();
    }

    @PostMapping("/reservations")
    public Reservation reserve(@RequestBody ReservationDto dto) throws SquareApiException {
        // Look up by PK with Cayenne
        Users user = Cayenne.objectForPK(context, Users.class, dto.getUserId());
        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, dto.getTeeTimeId());

        if (teeTime == null) {
            throw new RuntimeException("Tee time not found");
        }

        int partySize = dto.getPartySize();

        // look up price from the tier
        TeeTimeTier tier = teeTime.getTier();
        long totalAmount = tier.getPriceCents() * partySize;

        Money amount = Money.builder()
                .amount(totalAmount)
                .currency(Currency.USD)
                .build();

        CreatePaymentRequest paymentReq = CreatePaymentRequest.builder()
                .sourceId(dto.getSourceId())
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(squareLocationId)
                .build();

        // Call Payments API
        CreatePaymentResponse paymentResp = squareClient.payments().create(paymentReq);

        // Unwrap Optionals safely (v45 returns Optionals)
        var sqPayment = paymentResp.getPayment()
                .orElseThrow(() -> new RuntimeException("Square payment missing"));

        String paymentId = sqPayment.getId()
                .orElseThrow(() -> new RuntimeException("Square payment missing ID"));

        String status = sqPayment.getStatus().orElse("UNKNOWN");
        if (!"COMPLETED".equals(status)) {
            throw new RuntimeException("Payment failed with status: " + status);
        }

        // Persist reservation + payment
        Reservation reservation = context.newObject(Reservation.class);
        reservation.setUser(user);
        reservation.setTeeTime(teeTime);
        reservation.setPartySize(partySize);
        reservation.setCreatedAt(LocalDateTime.now());

        // decrement capacity
        teeTime.setCapacity(teeTime.getCapacity() - partySize);

        Payment payment = context.newObject(Payment.class);
        payment.setReservation(reservation);
        payment.setSquarePaymentId(paymentId);
        payment.setAmountCents(dto.getAmountCents());
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());

        context.commitChanges();
        return reservation;
    }

    @DeleteMapping("/reservations/{id}/cancel")
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
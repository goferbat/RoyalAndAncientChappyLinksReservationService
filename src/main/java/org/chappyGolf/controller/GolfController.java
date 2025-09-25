package org.chappyGolf.controller;

import com.squareup.square.SquareClient;
import com.squareup.square.core.SquareApiException;
import com.squareup.square.types.CreatePaymentRequest;
import com.squareup.square.types.CreatePaymentResponse;
import com.squareup.square.types.Currency;
import com.squareup.square.types.Money;
import org.chappyGolf.model.cayenne.Payment;
import org.chappyGolf.model.cayenne.Reservation;
import org.chappyGolf.model.cayenne.TeeTime;
import org.chappyGolf.model.cayenne.User;
import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.chappyGolf.dto.ReservationDto;
import org.chappyGolf.dto.TeeTimeDto;
import org.chappyGolf.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
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
    public User createUser(@RequestBody UserDto dto) {
        User user = context.newObject(User.class);
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        context.commitChanges();
        return user;
    }

    // ---- Tee Times ----
    @GetMapping("/tee-times")
    public List<TeeTime> listTeeTimes() {
        return ObjectSelect.query(TeeTime.class)
                .where(TeeTime.RESERVED.isFalse())
                .select(context);
    }

    @PostMapping("/tee-times")
    public TeeTime createTeeTime(@RequestBody TeeTimeDto dto) {
        TeeTime teeTime = context.newObject(TeeTime.class);
        teeTime.setSlot(dto.getSlot());
        teeTime.setReserved(false);
        context.commitChanges();
        return teeTime;
    }

    @PostMapping("/reservations")
    public Reservation reserve(@RequestBody ReservationDto dto) throws SquareApiException {
        // Look up by PK with Cayenne
        User user = Cayenne.objectForPK(context, User.class, dto.getUserId());
        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, dto.getTeeTimeId());
        if (teeTime == null || Boolean.TRUE.equals(teeTime.getReserved())) {
            throw new RuntimeException("Tee time unavailable");
        }

        // Build Money (v45 no-arg builder + fluent setters)
        Money amount = Money.builder()
                .amount(dto.getAmountCents())
                .currency(Currency.USD)
                .build();

        // Build CreatePaymentRequest (v45 no-arg builder)
        CreatePaymentRequest paymentReq = CreatePaymentRequest
                .builder()
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
        reservation.setCreatedAt(LocalDateTime.now());
        teeTime.setReserved(true);

        Payment payment = context.newObject(Payment.class);
        payment.setReservation(reservation);
        payment.setSquarePaymentId(paymentId);
        payment.setAmountCents(dto.getAmountCents());
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());

        context.commitChanges();
        return reservation;
    }
}
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GolfController {

    private final ObjectContext context;
    private final SquareClient squareClient;
    private final String squareLocationId;
    private final String teeTimeVariationId;
    private final org.chappyGolf.services.TeeTimeSeedService teeTimeSeedService;
    private final org.chappyGolf.services.EmailService emailService;

    public GolfController(ObjectContext context,
                          SquareClient squareClient,
                          @Value("${square.location.id}") String squareLocationId,
                          @Value("${square.tee-time.variation-id}") String teeTimeVariationId,
                          org.chappyGolf.services.TeeTimeSeedService teeTimeSeedService,
                          org.chappyGolf.services.EmailService emailService) {
        this.context = context;
        this.squareClient = squareClient;
        this.squareLocationId = squareLocationId;
        this.teeTimeVariationId = teeTimeVariationId;
        this.teeTimeSeedService = teeTimeSeedService;
        this.emailService = emailService;
    }

    // ---- Users ----
    @PostMapping("/users")
    public UserResponseDto createUser(@RequestBody UserDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("Name is required");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }

        Users existingUser = ObjectSelect.query(Users.class)
                .where(Users.EMAIL.eq(dto.getEmail().trim().toLowerCase()))
                .selectOne(context);

        if (existingUser != null) {
            return new UserResponseDto(
                    Cayenne.intPKForObject(existingUser),
                    existingUser.getName(),
                    existingUser.getEmail()
            );
        }

        Users user = context.newObject(Users.class);
        user.setName(dto.getName().trim());
        user.setEmail(dto.getEmail().trim().toLowerCase());
        user.setCreatedAt(LocalDateTime.now());
        user.setRole("CUSTOMER");
        user.setPasswordHash(null);

        context.commitChanges();

        return new UserResponseDto(
                Cayenne.intPKForObject(user),
                user.getName(),
                user.getEmail()
        );
    }

    @PostMapping("/client/reservations")
    public ReservationStatusResponse createClientReservation(@RequestBody ClientReservationDto dto) throws SquareApiException {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("Name is required");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (dto.getSourceId() == null || dto.getSourceId().isBlank()) {
            throw new RuntimeException("Payment source is required");
        }

        Users user = ObjectSelect.query(Users.class)
                .where(Users.EMAIL.eq(dto.getEmail().trim().toLowerCase()))
                .selectOne(context);

        if (user == null) {
            user = context.newObject(Users.class);
            user.setName(dto.getName().trim());
            user.setEmail(dto.getEmail().trim().toLowerCase());
            user.setCreatedAt(LocalDateTime.now());
            user.setRole("CUSTOMER");
            user.setPasswordHash(null);
        } else {
            user.setName(dto.getName().trim());
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("CUSTOMER");
            }
        }

        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, dto.getTeeTimeId());
        TeeTimeTier tier = Cayenne.objectForPK(context, TeeTimeTier.class, dto.getTeeTimeTierId());

        if (teeTime == null || tier == null) {
            throw new RuntimeException("Invalid booking data");
        }

        if (dto.getPartySize() < 1 || dto.getPartySize() > 6) {
            throw new RuntimeException("Party size must be between 1 and 6");
        }

        if (teeTime.getCapacity() < dto.getPartySize()) {
            throw new RuntimeException("Not enough capacity left");
        }

        long transportationCents = dto.isTransportation() ? 800L * dto.getPartySize() : 0L;
        long totalAmount = (tier.getPriceCents() * dto.getPartySize()) + transportationCents;

        String orderId = createTeeTimeOrder(dto.getPartySize(), tier.getPriceCents(), totalAmount, transportationCents);

        Money amount = Money.builder()
                .amount(totalAmount)
                .currency(Currency.USD)
                .build();

        CreatePaymentRequest paymentReq = CreatePaymentRequest.builder()
                .sourceId(dto.getSourceId())
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(squareLocationId)
                .orderId(orderId)
                .autocomplete(false)
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
        reservation.setTransportation(dto.isTransportation());
        reservation.setCreatedAt(LocalDateTime.now());

        teeTime.setCapacity(teeTime.getCapacity() - dto.getPartySize());

        Payment payment = context.newObject(Payment.class);
        payment.setReservation(reservation);
        payment.setSquarePaymentId(paymentId);
        payment.setAmountCents(totalAmount);
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());

        context.commitChanges();

        int reservationId = Cayenne.intPKForObject(reservation);

        try {
            emailService.sendReservationNotification(
                    user.getName(),
                    user.getEmail(),
                    teeTime.getStartTime(),
                    tier.getName(),
                    reservation.getPartySize(),
                    totalAmount,
                    reservationId
            );

            emailService.sendReservationConfirmationToCustomer(
                    user.getName(),
                    user.getEmail(),
                    teeTime.getStartTime(),
                    tier.getName(),
                    reservation.getPartySize(),
                    totalAmount,
                    reservationId
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ReservationStatusResponse(
                reservationId,
                status,
                reservation.getPartySize(),
                teeTime.getStartTime()
        );
    }

    // ---- Tee Times ----
    @GetMapping("/tee-times")
    public List<TeeTimeResponse> listTeeTimes() {
        List<TeeTime> teeTimes = ObjectSelect.query(TeeTime.class).select(context);
        List<TeeTimeTier> tiers = ObjectSelect.query(TeeTimeTier.class).select(context);

        return teeTimes.stream()
                .map(tt -> new TeeTimeResponse(
                        Cayenne.intPKForObject(tt),
                        tt.getStartTime(),
                        tt.getCapacity(),
                        tiers.stream()
                                .map(t -> new TeeTimeTierDto(Cayenne.intPKForObject(t), t.getName(), t.getPriceCents()))
                                .toList(),
                        tt.isBlocked(),
                        tt.getBlockedReason()
                ))
                .toList();
    }

    @PostMapping("/tee-times/post")
    public TeeTime createTeeTime(@RequestBody TeeTimeDto dto) {
        TeeTime teeTime = context.newObject(TeeTime.class);
        teeTime.setStartTime(dto.getstartTime());
        teeTime.setCapacity(dto.getCapacity());
        context.commitChanges();
        return teeTime;
    }

    @PostMapping("/tee-times/seed/{date}")
    public ResponseEntity<String> seedTeeTimes(@PathVariable String date) {
        teeTimeSeedService.seedForDate(LocalDate.parse(date));
        return ResponseEntity.ok("Seeded tee times for " + date);
    }

    @GetMapping("/reservations/{id}/status")
    public ReservationStatusResponse getReservationStatus(@PathVariable int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        GetPaymentsRequest req = GetPaymentsRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();

        var resp = squareClient.payments().get(req);
        var sqPayment = resp.getPayment().orElseThrow();

        String status = sqPayment.getStatus().orElse("UNKNOWN");

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
    public ReservationStatusResponse reserve(@RequestBody ReservationDto dto) throws SquareApiException {
        Users user = Cayenne.objectForPK(context, Users.class, dto.getUserId());
        TeeTime teeTime = Cayenne.objectForPK(context, TeeTime.class, dto.getTeeTimeId());
        TeeTimeTier tier = Cayenne.objectForPK(context, TeeTimeTier.class, dto.geTeeTimeTierId());

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

        String orderId = createTeeTimeOrder(dto.getPartySize(), tier.getPriceCents(), totalAmount, 0L);

        Money amount = Money.builder()
                .amount(totalAmount)
                .currency(Currency.USD)
                .build();

        CreatePaymentRequest paymentReq = CreatePaymentRequest.builder()
                .sourceId(dto.getSourceId())
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(squareLocationId)
                .orderId(orderId)
                .autocomplete(false)
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
        payment.setStatus(status);
        payment.setCreatedAt(LocalDateTime.now());

        context.commitChanges();

        int reservationId = (Integer) reservation.getObjectId()
                .getIdSnapshot()
                .get("id");

        try {
            emailService.sendReservationNotification(
                    user.getName(),
                    user.getEmail(),
                    teeTime.getStartTime(),
                    tier.getName(),
                    reservation.getPartySize(),
                    totalAmount,
                    reservationId
            );

            emailService.sendReservationConfirmationToCustomer(
                    user.getName(),
                    user.getEmail(),
                    teeTime.getStartTime(),
                    tier.getName(),
                    reservation.getPartySize(),
                    totalAmount,
                    reservationId
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ReservationStatusResponse(
                reservationId,
                status,
                reservation.getPartySize(),
                teeTime.getStartTime()
        );
    }


    private String createTeeTimeOrder(int quantity, long unitPriceCents, long totalAmount, long transportationCents) throws SquareApiException {
        List<OrderLineItem> lineItems = new java.util.ArrayList<>();

        lineItems.add(OrderLineItem.builder()
                .quantity(String.valueOf(quantity))
                .catalogObjectId(teeTimeVariationId)
                .basePriceMoney(Money.builder()
                        .amount(unitPriceCents)
                        .currency(Currency.USD)
                        .build())
                .build());

        if (transportationCents > 0) {
            lineItems.add(OrderLineItem.builder()
                    .quantity(String.valueOf(quantity))
                    .name("Transportation")
                    .basePriceMoney(Money.builder()
                            .amount(800L)
                            .currency(Currency.USD)
                            .build())
                    .build());
        }

        Order order = Order.builder()
                .locationId(squareLocationId)
                .lineItems(lineItems)
                .build();

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .order(order)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        CreateOrderResponse orderResponse = squareClient.orders().create(orderRequest);

        return orderResponse.getOrder()
                .orElseThrow(() -> new RuntimeException("Order creation failed"))
                .getId()
                .orElseThrow(() -> new RuntimeException("Order ID missing"));
    }

    public ReservationStatusResponse captureReservationPayment(int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) {
            throw new RuntimeException("Reservation not found");
        }

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) {
            throw new RuntimeException("Payment not found");
        }

        var req = CompletePaymentRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();

        var resp = squareClient.payments().complete(req);

        var sqPayment = resp.getPayment()
                .orElseThrow(() -> new RuntimeException("Square payment missing after capture"));

        String status = sqPayment.getStatus().orElse("UNKNOWN");

        if (!"COMPLETED".equals(status)) {
            throw new RuntimeException("Capture failed, status: " + status);
        }

        payment.setStatus(status);
        context.commitChanges();

        int reservationId = (Integer) reservation.getObjectId()
                .getIdSnapshot()
                .get("id");

        long totalAmount = payment.getAmountCents();

        try {
            emailService.sendReservationCapturedNotification(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    reservation.getTeeTime().getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    reservation.isTransportation(),
                    totalAmount,
                    reservationId
            );

            emailService.sendReservationCapturedConfirmationToCustomer(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    reservation.getTeeTime().getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    reservation.isTransportation(),
                    totalAmount,
                    reservationId
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ReservationStatusResponse(
                reservationId,
                status,
                reservation.getPartySize(),
                reservation.getTeeTime().getStartTime()
        );
    }

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

        TeeTime teeTime = reservation.getTeeTime();
        teeTime.setCapacity(teeTime.getCapacity() + reservation.getPartySize());

        payment.setStatus("CANCELED");
        context.commitChanges();
    }

    public void refundReservation(@PathVariable int id) throws com.squareup.square.core.SquareApiException {
        Reservation reservation = org.apache.cayenne.Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = org.apache.cayenne.query.ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("No payment found for reservation " + id);

        TeeTime teeTime = reservation.getTeeTime();

        System.out.println(Money.builder().amount(payment.getAmountCents()).currency(Currency.USD).build());

        RefundPaymentRequest refundReq = RefundPaymentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(Money.builder()
                        .amount(payment.getAmountCents())
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

        teeTime.setCapacity(teeTime.getCapacity() + reservation.getPartySize());
        payment.setStatus("REFUNDED");
        context.commitChanges();
    }

    public ReservationStatusResponse noShowReservation(int id) throws SquareApiException {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, id);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        if (!"AUTHORIZED".equals(payment.getStatus()) && !"APPROVED".equals(payment.getStatus())) {
            throw new RuntimeException("Payment is not in an authorized state (current: " + payment.getStatus() + ")");
        }

        CompletePaymentRequest captureReq = CompletePaymentRequest.builder()
                .paymentId(payment.getSquarePaymentId())
                .build();
        squareClient.payments().complete(captureReq);

        long refundAmount = payment.getAmountCents() / 2;
        RefundPaymentRequest refundReq = RefundPaymentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(Money.builder()
                        .amount(refundAmount)
                        .currency(Currency.USD)
                        .build())
                .paymentId(payment.getSquarePaymentId())
                .reason("No-show: 50% charge per cancellation policy")
                .build();
        squareClient.refunds().refundPayment(refundReq);

        payment.setAmountCents(payment.getAmountCents() - refundAmount);
        payment.setStatus("NO_SHOW");
        context.commitChanges();

        try {
            emailService.sendNoShowChargeNotification(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    reservation.getTeeTime().getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    payment.getAmountCents(),
                    Cayenne.intPKForObject(reservation)
            );

            emailService.sendNoShowChargeConfirmationToCustomer(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    reservation.getTeeTime().getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    payment.getAmountCents(),
                    Cayenne.intPKForObject(reservation)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ReservationStatusResponse(
                Cayenne.intPKForObject(reservation),
                "NO_SHOW",
                reservation.getPartySize(),
                reservation.getTeeTime().getStartTime()
        );
    }

    public ReservationStatusResponse moveReservation(int reservationId, int targetTeeTimeId) {
        Reservation reservation = Cayenne.objectForPK(context, Reservation.class, reservationId);
        if (reservation == null) throw new RuntimeException("Reservation not found");

        Payment payment = ObjectSelect.query(Payment.class)
                .where(Payment.RESERVATION.eq(reservation))
                .selectOne(context);
        if (payment == null) throw new RuntimeException("Payment not found");

        if ("CANCELED".equals(payment.getStatus()) || "NO_SHOW".equals(payment.getStatus())) {
            throw new RuntimeException("Cannot move a reservation with payment status: " + payment.getStatus());
        }

        TeeTime targetTeeTime = Cayenne.objectForPK(context, TeeTime.class, targetTeeTimeId);
        if (targetTeeTime == null) throw new RuntimeException("Target tee time not found");

        if (targetTeeTime.isBlocked()) {
            throw new RuntimeException("Target tee time is blocked");
        }

        if (targetTeeTime.getCapacity() < reservation.getPartySize()) {
            throw new RuntimeException("Target tee time does not have enough capacity for party of " + reservation.getPartySize());
        }

        TeeTime previousTeeTime = reservation.getTeeTime();
        LocalDateTime previousStartTime = previousTeeTime.getStartTime();

        previousTeeTime.setCapacity(previousTeeTime.getCapacity() + reservation.getPartySize());
        targetTeeTime.setCapacity(targetTeeTime.getCapacity() - reservation.getPartySize());
        reservation.setTeeTime(targetTeeTime);

        context.commitChanges();

        int id = Cayenne.intPKForObject(reservation);

        try {
            emailService.sendMoveNotificationToAdmin(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    previousStartTime,
                    targetTeeTime.getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    id
            );
            emailService.sendMoveConfirmationToCustomer(
                    reservation.getUser().getName(),
                    reservation.getUser().getEmail(),
                    previousStartTime,
                    targetTeeTime.getStartTime(),
                    reservation.getTier().getName(),
                    reservation.getPartySize(),
                    id
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ReservationStatusResponse(
                id,
                payment.getStatus(),
                reservation.getPartySize(),
                targetTeeTime.getStartTime()
        );
    }
}
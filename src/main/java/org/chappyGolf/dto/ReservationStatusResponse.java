package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class ReservationStatusResponse {
    private int reservationId;
    private String paymentStatus;  // AUTHORIZED, COMPLETED, CANCELED, etc.
    private int partySize;
    private LocalDateTime teeTimeSlot;

    public ReservationStatusResponse(int reservationId, String paymentStatus,
                                     int partySize, LocalDateTime teeTimeSlot) {
        this.reservationId = reservationId;
        this.paymentStatus = paymentStatus;
        this.partySize = partySize;
        this.teeTimeSlot = teeTimeSlot;
    }

    public int getReservationId() { return reservationId; }
    public String getPaymentStatus() { return paymentStatus; }
    public int getPartySize() { return partySize; }
    public LocalDateTime getTeeTimeSlot() { return teeTimeSlot; }
}

package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class AdminReservationDetailResponse {
    private int reservationId;
    private int userId;
    private String customerName;
    private String customerEmail;
    private int teeTimeId;
    private LocalDateTime startTime;
    private String tierName;
    private int partySize;
    private boolean transportation;
    private long amountCents;
    private String paymentStatus;
    private String squarePaymentId;

    public AdminReservationDetailResponse(
            int reservationId,
            int userId,
            String customerName,
            String customerEmail,
            int teeTimeId,
            LocalDateTime startTime,
            String tierName,
            int partySize,
            boolean transportation,
            long amountCents,
            String paymentStatus,
            String squarePaymentId
    ) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.teeTimeId = teeTimeId;
        this.startTime = startTime;
        this.tierName = tierName;
        this.partySize = partySize;
        this.transportation = transportation;
        this.amountCents = amountCents;
        this.paymentStatus = paymentStatus;
        this.squarePaymentId = squarePaymentId;
    }

    public int getReservationId() { return reservationId; }
    public int getUserId() { return userId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getTeeTimeId() { return teeTimeId; }
    public LocalDateTime getStartTime() { return startTime; }
    public String getTierName() { return tierName; }
    public int getPartySize() { return partySize; }
    public boolean getTransportation() {return this.transportation; }
    public long getAmountCents() { return amountCents; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getSquarePaymentId() { return squarePaymentId; }
}
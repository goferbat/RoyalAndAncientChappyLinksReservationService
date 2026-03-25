package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class TeeSheetReservationDto {

    private int reservationId;
    private int teeTimeId;

    private String customerName;
    private String customerEmail;

    private int partySize;

    private LocalDateTime startTime;
    private String slotLabel; // optional but very useful for UI

    private String tierName;
    private long amountCents;

    private String paymentStatus;

    public TeeSheetReservationDto(
            int reservationId,
            int teeTimeId,
            String customerName,
            String customerEmail,
            int partySize,
            LocalDateTime startTime,
            String slotLabel,
            String tierName,
            long amountCents,
            String paymentStatus
    ) {
        this.reservationId = reservationId;
        this.teeTimeId = teeTimeId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.partySize = partySize;
        this.startTime = startTime;
        this.slotLabel = slotLabel;
        this.tierName = tierName;
        this.amountCents = amountCents;
        this.paymentStatus = paymentStatus;
    }

    public int getReservationId() { return reservationId; }
    public int getTeeTimeId() { return teeTimeId; }

    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }

    public int getPartySize() { return partySize; }

    public LocalDateTime getStartTime() { return startTime; }
    public String getSlotLabel() { return slotLabel; }

    public String getTierName() { return tierName; }
    public long getAmountCents() { return amountCents; }

    public String getPaymentStatus() { return paymentStatus; }
}
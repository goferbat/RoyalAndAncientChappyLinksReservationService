package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class TeeSheetReservationDto {
    private Integer reservationId;
    private Integer teeTimeId;
    private String customerName;
    private String customerEmail;
    private int partySize;
    private LocalDateTime startTime;
    private String slotLabel;
    private String tierName;
    private long amountCents;
    private String paymentStatus;
    private boolean transportation;

    public TeeSheetReservationDto(
            Integer reservationId,
            Integer teeTimeId,
            String customerName,
            String customerEmail,
            int partySize,
            LocalDateTime startTime,
            String slotLabel,
            String tierName,
            long amountCents,
            String paymentStatus,
            boolean transportation
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
        this.transportation = transportation;
    }

    public boolean isTransportation() {
        return transportation;
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
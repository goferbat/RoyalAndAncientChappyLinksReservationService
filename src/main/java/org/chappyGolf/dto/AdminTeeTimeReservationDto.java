package org.chappyGolf.dto;

public record AdminTeeTimeReservationDto(
        Integer reservationId,
        String customerName,
        String customerEmail,
        Integer partySize,
        String tierName,
        long amountCents,
        String paymentStatus,
        boolean transportation
) {}
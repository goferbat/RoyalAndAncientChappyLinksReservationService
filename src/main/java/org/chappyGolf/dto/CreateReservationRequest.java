package org.chappyGolf.dto;

public record CreateReservationRequest(
        String name,
        String time,
        String email
) {

}

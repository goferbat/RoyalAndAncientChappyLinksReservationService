package org.chappyGolf.dto;

public record TeeTimeStatus(
        String time,
        int capacity,
        int booked,
        int remaining
) {

}


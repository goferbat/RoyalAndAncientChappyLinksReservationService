package org.chappyGolf.dto;

public record UpdateTeeTimeBlockRequest(
        boolean blocked,
        String blockedReason
) {}
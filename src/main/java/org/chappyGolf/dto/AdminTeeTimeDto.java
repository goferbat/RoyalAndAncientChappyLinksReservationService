package org.chappyGolf.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminTeeTimeDto(
        Integer teeTimeId,
        LocalDateTime startTime,
        String slotLabel,
        Integer capacity,
        Integer spotsRemaining,
        boolean blocked,
        String blockedReason,
        List<AdminTeeTimeReservationDto> reservations
) {}
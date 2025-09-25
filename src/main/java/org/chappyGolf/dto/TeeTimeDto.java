package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class TeeTimeDto {
    private LocalDateTime slot;
    // getters/setters

    public LocalDateTime getSlot() {
        return slot;
    }

    public void setSlot(LocalDateTime slot) {
        this.slot = slot;
    }
}
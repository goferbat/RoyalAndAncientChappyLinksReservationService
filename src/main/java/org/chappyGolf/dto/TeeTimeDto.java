package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class TeeTimeDto {
    private LocalDateTime slot;
    private Integer capacity;
    // getters/setters

    public LocalDateTime getSlot() {
        return slot;
    }

    public void setSlot(LocalDateTime slot) {
        this.slot = slot;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
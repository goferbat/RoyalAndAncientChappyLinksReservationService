package org.chappyGolf.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TeeTimeResponse {
    private int id;
    private LocalDateTime slot;
    private int capacity;
    private List<TeeTimeTierDto> tiers;

    public TeeTimeResponse(int id, LocalDateTime slot, int capacity, List<TeeTimeTierDto> tiers) {
        this.id = id;
        this.slot = slot;
        this.capacity = capacity;
        this.tiers = tiers;
    }
    // getters

    public int getId() {
        return id;
    }

    public LocalDateTime getSlot() {
        return slot;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<TeeTimeTierDto> getTiers() {
        return tiers;
    }
}

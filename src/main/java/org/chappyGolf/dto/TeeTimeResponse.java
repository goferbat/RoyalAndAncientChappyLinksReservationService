package org.chappyGolf.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TeeTimeResponse {
    private int id;
    private LocalDateTime startTime;
    private int capacity;
    private List<TeeTimeTierDto> tiers;

    public TeeTimeResponse(int id, LocalDateTime startTime, int capacity, List<TeeTimeTierDto> tiers) {
        this.id = id;
        this.startTime = startTime;
        this.capacity = capacity;
        this.tiers = tiers;
    }
    // getters

    public int getId() {
        return id;
    }

    public LocalDateTime getstartTime() {
        return startTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<TeeTimeTierDto> getTiers() {
        return tiers;
    }
}

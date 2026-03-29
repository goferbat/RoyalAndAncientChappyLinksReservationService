package org.chappyGolf.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TeeTimeResponse {
    private int id;
    private LocalDateTime startTime;
    private int capacity;
    private Boolean blocked;
    private String blockedReason;
    private List<TeeTimeTierDto> tiers;

    public TeeTimeResponse(int id, LocalDateTime startTime, int capacity, List<TeeTimeTierDto> tiers, Boolean blocked, String blockedReason) {
        this.id = id;
        this.startTime = startTime;
        this.capacity = capacity;
        this.tiers = tiers;
        this.blocked = blocked;
        this.blockedReason = blockedReason;
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

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }
}

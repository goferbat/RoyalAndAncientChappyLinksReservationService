package org.chappyGolf.dto;

import java.time.LocalDateTime;

public class TeeTimeDto {
    private LocalDateTime startTime;
    private Integer capacity;
    // getters/setters

    public LocalDateTime getstartTime() {
        return startTime;
    }

    public void setstartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
package org.chappyGolf.dto;

public class ReservationDto {
    private int userId;
    private int teeTimeId;
    private String sourceId;
    private long amountCents;
    private int partySize; // number of players (1–6)
    private int teeTimeTierId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTeeTimeId() {
        return teeTimeId;
    }

    public void setTeeTimeId(int teeTimeId) {
        this.teeTimeId = teeTimeId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
    }

    public int geTeeTimeTierId() {
        return teeTimeTierId;
    }

    public void setteeTimeTierId(int teeTimeTierId) {
        this.teeTimeTierId = teeTimeTierId;
    }
    // getters/setters
}
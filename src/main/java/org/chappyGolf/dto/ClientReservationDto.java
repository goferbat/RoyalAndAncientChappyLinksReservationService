package org.chappyGolf.dto;

public class ClientReservationDto {
    private String name;
    private String email;
    private int teeTimeId;
    private String sourceId;
    private int partySize;
    private int teeTimeTierId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
    }

    public int getTeeTimeTierId() {
        return teeTimeTierId;
    }

    public void setTeeTimeTierId(int teeTimeTierId) {
        this.teeTimeTierId = teeTimeTierId;
    }
}
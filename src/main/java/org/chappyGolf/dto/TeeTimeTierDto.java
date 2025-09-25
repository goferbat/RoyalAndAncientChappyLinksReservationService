package org.chappyGolf.dto;

public class TeeTimeTierDto {
    private int id;
    private String name;
    private long priceCents;

    public TeeTimeTierDto(int id, String name, long priceCents) {
        this.id = id;
        this.name = name;
        this.priceCents = priceCents;
    }
    // getters

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPriceCents() {
        return priceCents;
    }
}
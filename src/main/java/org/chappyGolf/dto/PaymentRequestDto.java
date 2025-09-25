package org.chappyGolf.dto;

public class PaymentRequestDto {
    private String sourceId;
    private long amountCents;
    // getters/setters

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
}
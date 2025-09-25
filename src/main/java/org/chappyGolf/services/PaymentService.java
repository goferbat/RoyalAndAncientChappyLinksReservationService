package org.chappyGolf.services;

import com.squareup.square.SquareClient;
import com.squareup.square.types.CreatePaymentRequest;
import com.squareup.square.types.CreatePaymentResponse;
import com.squareup.square.types.Currency;
import com.squareup.square.types.Money;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private final SquareClient client;

    public PaymentService(SquareClient client) {
        this.client = client;
    }

    public CreatePaymentResponse takePayment(String sourceId, long amountCents, String locationId) throws Exception {
        // Build Money
        Money amount = Money.builder()
                .amount(amountCents)       // in cents
                .currency(Currency.USD)    // enum
                .build();

        // Build payment request
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceId(sourceId)
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(locationId)
                .build();

        // Call Square Payments API
        return client.payments().create(request);
    }
}
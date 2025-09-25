package org.chappyGolf.controller;

import com.squareup.square.SquareClient;
import com.squareup.square.types.CreatePaymentRequest;
import com.squareup.square.types.CreatePaymentResponse;
import com.squareup.square.types.Currency;
import com.squareup.square.types.Money;
import org.chappyGolf.dto.PaymentRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final SquareClient squareClient;
    private final String squareLocationId;

    public PaymentController(SquareClient squareClient,
                             @Value("${square.location.id}") String squareLocationId) {
        this.squareClient = squareClient;
        this.squareLocationId = squareLocationId;
    }

    @PostMapping
    public CreatePaymentResponse createPayment(@RequestBody PaymentRequestDto dto) throws Exception {
        Money amount = Money.builder()
                .amount(dto.getAmountCents())
                .currency(Currency.USD)
                .build();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .sourceId(dto.getSourceId())
                .idempotencyKey(UUID.randomUUID().toString())
                .amountMoney(amount)
                .locationId(squareLocationId)
                .build();

        return squareClient.payments().create(request);
    }
}
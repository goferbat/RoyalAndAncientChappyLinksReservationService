package org.chappyGolf.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String resendApiKey;
    private final String fromEmail;
    private final String adminEmail;

    public EmailService(
            @Value("${resend.api.key}") String resendApiKey,
            @Value("${app.mail.from}") String fromEmail,
            @Value("${app.mail.admin}") String adminEmail
    ) {
        this.resendApiKey = resendApiKey;
        this.fromEmail = fromEmail;
        this.adminEmail = adminEmail;
    }

    private void sendEmail(String to, String subject, String text) {
        String url = "https://api.resend.com/emails";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resendApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "RoyalChappy/1.0");

        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", List.of(to),
                "subject", subject,
                "text", text
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to send email with Resend", e);
        }
    }

    public void sendReservationNotification(
            String customerName,
            String customerEmail,
            LocalDateTime teeTime,
            String tierName,
            int partySize,
            long amountCents,
            int reservationId
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' h:mm a");
        String formattedTeeTime = teeTime.atZone(ZoneId.of("America/New_York")).format(formatter);
        String dollars = String.format("%.2f", amountCents / 100.0);

        String text =
                "A new reservation was made.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Email: " + customerEmail + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Authorized Amount: $" + dollars + "\n";

        sendEmail(adminEmail, "New Golf Reservation #" + reservationId, text);
    }

    public void sendReservationConfirmationToCustomer(
            String customerName,
            String customerEmail,
            LocalDateTime teeTime,
            String tierName,
            int partySize,
            long amountCents,
            int reservationId
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' h:mm a");
        String formattedTeeTime = teeTime.atZone(ZoneId.of("America/New_York")).format(formatter);
        String dollars = String.format("%.2f", amountCents / 100.0);

        String text =
                "Hi " + customerName + ",\n\n" +
                        "Your reservation is confirmed.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Authorized Amount: $" + dollars + "\n\n" +
                        "We look forward to seeing you.";

        sendEmail(customerEmail, "Your Chappy Golf Reservation #" + reservationId, text);
    }

    public void sendReservationCapturedNotification(
            String customerName,
            String customerEmail,
            LocalDateTime teeTime,
            String tierName,
            int partySize,
            Boolean transportation,
            long amountCents,
            int reservationId
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' h:mm a");
        String formattedTeeTime = teeTime.atZone(ZoneId.of("America/New_York")).format(formatter);
        String formattedAmount = String.format("$%.2f", amountCents / 100.0);

        String text =
                "A reservation has now been confirmed and charged.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Customer Email: " + customerEmail + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Transportation: " + transportation + "\n" +
                        "Amount Charged: " + formattedAmount + "\n";

        sendEmail(adminEmail, "Reservation Confirmed + Charged #" + reservationId, text);
    }

    public void sendReservationCapturedConfirmationToCustomer(
            String customerName,
            String customerEmail,
            LocalDateTime teeTime,
            String tierName,
            int partySize,
            Boolean transportation,
            long amountCents,
            int reservationId
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d yyyy 'at' h:mm a");
        String formattedTeeTime = teeTime.atZone(ZoneId.of("America/New_York")).format(formatter);
        String formattedAmount = String.format("$%.2f", amountCents / 100.0);

        String text =
                "Hi " + customerName + ",\n\n" +
                        "Your reservation has now been confirmed, and your card has been charged.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Transportation: " + transportation + "\n" +
                        "Amount Charged: " + formattedAmount + "\n\n" +
                        "We look forward to seeing you.";

        sendEmail(customerEmail, "Your Reservation Is Confirmed #" + reservationId, text);
    }
}
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

    public void sendNoShowChargeNotification(
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
        String formattedAmount = String.format("$%.2f", amountCents / 100.0);

        String text =
                "A no-show charge has been applied.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Customer Email: " + customerEmail + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Amount Charged (50%): " + formattedAmount + "\n";

        sendEmail(adminEmail, "No-Show Charge Applied #" + reservationId, text);
    }

    public void sendNoShowChargeConfirmationToCustomer(
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
        String formattedAmount = String.format("$%.2f", amountCents / 100.0);

        String text =
                "Hi " + customerName + ",\n\n" +
                        "We missed you today at Royal Chappy. As per our cancellation policy, " +
                        "a no-show fee of 50% of your reservation total has been charged to your card.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Amount Charged: " + formattedAmount + "\n\n" +
                        "If you have any questions, please don't hesitate to reach out. " +
                        "We hope to see you on the Chappy side soon.";

        sendEmail(customerEmail, "No-Show Fee Applied – Reservation #" + reservationId, text);
    }

    public void sendMoveConfirmationToCustomer(
            String name, String email,
            LocalDateTime previousStartTime, LocalDateTime newStartTime,
            String tierName, int partySize, int reservationId) {

        String subject = "Your Royal Chappy tee time has been updated";
        String body = String.format(
                "Hi %s,\n\n" +
                        "Your reservation (#%d) has been moved.\n\n" +
                        "Previous time: %s\n" +
                        "New time:      %s\n\n" +
                        "Tier: %s\n" +
                        "Party size: %d\n\n" +
                        "If you have any questions, please call the clubhouse at 508-939-4055.\n\n" +
                        "See you on the Chappy side.\n" +
                        "— Royal Chappy",
                name,
                reservationId,
                previousStartTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")),
                newStartTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")),
                tierName,
                partySize
        );

        sendEmail(email, subject, body);
    }

    public void sendMoveNotificationToAdmin(
            String name, String email,
            LocalDateTime previousStartTime, LocalDateTime newStartTime,
            String tierName, int partySize, int reservationId) {

        String subject = "[Admin] Reservation moved: " + name;
        String body = String.format(
                "Reservation #%d for %s (%s) has been moved.\n\n" +
                        "From: %s\n" +
                        "To:   %s\n\n" +
                        "Tier: %s | Party size: %d",
                reservationId,
                name,
                email,
                previousStartTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")),
                newStartTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")),
                tierName,
                partySize
        );

        sendEmail(adminEmail, subject, body);
    }

}
package org.chappyGolf.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String adminEmail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromEmail,
                        @Value("${app.mail.admin}") String adminEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.adminEmail = adminEmail;
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(adminEmail);
        message.setSubject("New Golf Reservation #" + reservationId);
        message.setText(
                "A new reservation was made.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Email: " + customerEmail + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Authorized Amount: $" + dollars + "\n"
        );

        mailSender.send(message);
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(customerEmail);
        message.setSubject("Your Chappy Golf Reservation #" + reservationId);
        message.setText(
                "Hi " + customerName + ",\n\n" +
                        "Your reservation is confirmed.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Authorized Amount: $" + dollars + "\n\n" +
                        "We look forward to seeing you."
        );

        mailSender.send(message);
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(adminEmail);
        message.setSubject("Reservation Confirmed + Charged #" + reservationId);
        message.setText(
                "A reservation has now been confirmed and charged.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Customer Email: " + customerEmail + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Transportation: " + transportation + "\n" +
                        "Amount Charged: " + formattedAmount + "\n"
        );

        mailSender.send(message);
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(customerEmail);
        message.setSubject("Your Reservation Is Confirmed #" + reservationId);
        message.setText(
                "Hi " + customerName + ",\n\n" +
                        "Your reservation has now been confirmed, and your card has been charged.\n\n" +
                        "Reservation ID: " + reservationId + "\n" +
                        "Tee Time: " + formattedTeeTime + "\n" +
                        "Tier: " + tierName + "\n" +
                        "Party Size: " + partySize + "\n" +
                        "Transporation: " + transportation + "\n" +
                        "Amount Charged: " + formattedAmount + "\n\n" +
                        "We look forward to seeing you."
        );

        mailSender.send(message);
    }
}
package org.chappyGolf.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.chappyGolf.dto.Reservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendReservationEmail(String toEmail, Reservation res) {
        String subject = "Your Tee Time is Booked – " + res.time();
        String cancelUrl = baseUrl + "/api/reservations/" + res.id();
        String html = """
      <div style="font-family:Arial,Helvetica,sans-serif">
        <h2>Reservation Confirmed</h2>
        <p><b>Time:</b> %s</p>
        <p><b>Name:</b> %s</p>
        <p><b>Confirmation ID:</b> %s</p>
        <p>You can cancel using this ID at any time.</p>
        <p>If needed, call our pro shop or use: <code>%s</code></p>
      </div>
      """.formatted(res.time(), res.name(), res.id(), cancelUrl);

        sendHtml(toEmail, subject, html);
    }

    @Async
    public void sendCancellationEmail(String toEmail, Reservation res) {
        String subject = "Reservation Cancelled – " + res.time();
        String html = """
      <div style="font-family:Arial,Helvetica,sans-serif">
        <h2>Reservation Cancelled</h2>
        <p><b>Time:</b> %s</p>
        <p><b>Name:</b> %s</p>
        <p><b>Confirmation ID:</b> %s</p>
        <p>We hope to see you again soon.</p>
      </div>
      """.formatted(res.time(), res.name(), res.id());
        sendHtml(toEmail, subject, html);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            // log and continue; avoid breaking the booking flow
            System.err.println("Email send failed: " + e.getMessage());
        }
    }
}
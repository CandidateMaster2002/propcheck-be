package com.propchk.be.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@propcheck.in}")
    private String fromAddress;

    @Value("${portal.frontend.url:https://propchck.netlify.app}")
    private String portalUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a welcome email to a newly registered customer.
     *
     * @param toEmail    customer's email address
     * @param toName     customer's display name
     * @param propertyAddress  the property address being inspected
     */
    public void sendCustomerWelcomeEmail(String toEmail, String toName, String propertyAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "PropCheck Team");
            helper.setTo(toEmail);
            helper.setSubject("Welcome to PropCheck – Your Inspection is Scheduled");

            String htmlBody = buildWelcomeHtml(toName, propertyAddress);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (MailException | MessagingException e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Sends a booking confirmation email with the 6 PM cutoff notice.
     *
     * @param toEmail           customer's email address
     * @param toName            customer's display name
     * @param propertyAddress   the property being inspected
     * @param inspectionDate    formatted inspection date (e.g. "12 November 2026")
     * @param inspectionTime    formatted inspection time slot (e.g. "09:30 AM")
     */
    public void sendBookingConfirmationEmail(String toEmail, String toName,
                                              String propertyAddress,
                                              String inspectionDate, String inspectionTime) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "PropCheck Team");
            helper.setTo(toEmail);
            helper.setSubject("Booking Confirmed – PropCheck Inspection on " + inspectionDate);

            String htmlBody = buildConfirmationHtml(toName, propertyAddress, inspectionDate, inspectionTime);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Booking confirmation email sent to {} for date {}", toEmail, inspectionDate);
        } catch (MailException | MessagingException e) {
            log.error("Failed to send confirmation email to {}: {}", toEmail, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending confirmation email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ─── HTML Builders ───────────────────────────────────────────────────────

    private String buildWelcomeHtml(String name, String propertyAddress) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;color:#333;'>"
                + "<div style='max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px;'>"
                + "<h2 style='color:#1d4ed8;'>Welcome to PropCheck!</h2>"
                + "<p>Dear <strong>" + escapeHtml(name) + "</strong>,</p>"
                + "<p>Thank you for registering with <strong>PropCheck</strong>. We are delighted to assist you "
                + "with the inspection of your property at:</p>"
                + "<blockquote style='background:#f3f4f6;padding:12px;border-left:4px solid #1d4ed8;margin:16px 0;'>"
                + "<strong>" + escapeHtml(propertyAddress) + "</strong></blockquote>"
                + "<p>Our team will reach out shortly to schedule your inspection at the earliest available slot.</p>"
                + "<p style='margin-top:24px;'>If you have any questions, please contact us at "
                + "<a href='mailto:support@propcheck.in'>support@propcheck.in</a>.</p>"
                + "<hr style='margin:24px 0;border:none;border-top:1px solid #e5e7eb;'/>"
                + "<p style='font-size:12px;color:#9ca3af;'>PropCheck | Quality Property Inspections</p>"
                + "</div></body></html>";
    }

    private String buildConfirmationHtml(String name, String propertyAddress,
                                          String inspectionDate, String inspectionTime) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;color:#333;'>"
                + "<div style='max-width:600px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:8px;'>"
                + "<h2 style='color:#1d4ed8;'>Booking Confirmed – PropCheck</h2>"
                + "<p>Dear <strong>" + escapeHtml(name) + "</strong>,</p>"
                + "<p>Your property inspection has been successfully booked. Here are your details:</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:16px 0;'>"
                + "<tr><td style='padding:8px;background:#f3f4f6;font-weight:bold;width:40%;'>Property</td>"
                + "<td style='padding:8px;'>" + escapeHtml(propertyAddress) + "</td></tr>"
                + "<tr><td style='padding:8px;background:#f3f4f6;font-weight:bold;'>Inspection Date</td>"
                + "<td style='padding:8px;'>" + escapeHtml(inspectionDate) + "</td></tr>"
                + "<tr><td style='padding:8px;background:#f3f4f6;font-weight:bold;'>Time Slot</td>"
                + "<td style='padding:8px;'>" + escapeHtml(inspectionTime) + "</td></tr>"
                + "</table>"
                + "<div style='background:#fef3c7;border:1px solid #f59e0b;border-radius:6px;padding:14px;margin:20px 0;'>"
                + "<strong>&#9888; Important Notice:</strong><br/>"
                + "If you need to reschedule or cancel your inspection, please communicate with us "
                + "<strong>before 6:00 PM on the day prior to your scheduled inspection</strong>. "
                + "Requests received after this cutoff cannot be guaranteed."
                + "</div>"
                + "<p>You can manage your booking anytime at "
                + "<a href='" + portalUrl + "'>" + portalUrl + "</a>.</p>"
                + "<hr style='margin:24px 0;border:none;border-top:1px solid #e5e7eb;'/>"
                + "<p style='font-size:12px;color:#9ca3af;'>PropCheck | Quality Property Inspections</p>"
                + "</div></body></html>";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}

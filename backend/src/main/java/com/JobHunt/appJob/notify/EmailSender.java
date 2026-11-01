package com.JobHunt.appJob.notify;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends multipart (HTML + plain-text) emails via Spring's {@link JavaMailSender}.
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String toEmail;
    private final boolean mailEnabled;

    public EmailSender(@Autowired(required = false) JavaMailSender mailSender,
                       EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.fromEmail = emailProperties.from();
        this.toEmail = emailProperties.to();
        this.mailEnabled = mailSender != null
                && fromEmail != null && !fromEmail.isBlank()
                && toEmail   != null && !toEmail.isBlank();
    }

    public boolean send(DigestEmail digest) {
        if (!mailEnabled) {
            log.warn("Email not configured (mailSender={}, from='{}', to='{}'). Skipping.",
                    mailSender != null, fromEmail, toEmail);
            return false;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(digest.subject());
            helper.setText(digest.textBody(), digest.htmlBody());
            mailSender.send(msg);
            log.info("Digest email sent to {} ({} job(s))", toEmail, digest.jobs().size());
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send digest email", e);
            return false;
        }
    }
}


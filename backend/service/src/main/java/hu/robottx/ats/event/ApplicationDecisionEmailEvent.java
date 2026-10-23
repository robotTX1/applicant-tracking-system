package hu.robottx.ats.event;

public record ApplicationDecisionEmailEvent(
        Long applicationId,
        String recipientEmail,
        String subject,
        String body
) {}

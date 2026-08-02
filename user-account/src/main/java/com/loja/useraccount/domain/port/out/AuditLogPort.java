package com.loja.useraccount.domain.port.out;

/** Output port for authentication audit logging. */
public interface AuditLogPort {
    /**
     * Records an authentication event.
     *
     * @param userId    the ID of the user involved (the subject of the event)
     * @param actorId   the ID of the user who performed the action (nullable; null when the actor is the subject)
     * @param eventType event label (e.g. LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_CHANGE, ROLE_ASSIGNED, LOGOUT)
     * @param ipAddress originating IP address (nullable)
     * @param userAgent browser/client user-agent string (nullable)
     * @param details   free-form detail message (nullable)
     */
    void logEvent(String userId, String actorId, String eventType, String ipAddress, String userAgent, String details);
}

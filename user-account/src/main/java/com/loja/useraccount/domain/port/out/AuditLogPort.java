package com.loja.useraccount.domain.port.out;

/** Output port for authentication audit logging. */
public interface AuditLogPort {
    /**
     * Records an authentication event.
     *
     * @param userId     the ID of the user involved (the subject of the event; nullable)
     * @param actorId    the ID of the user who performed the action (nullable; null when the actor is the subject)
     * @param eventType  event label (e.g. LOGIN_SUCCESS, LOGIN_FAILED, PASSWORD_CHANGE, ROLE_ASSIGNED, LOGOUT)
     * @param entityType the type of entity the event refers to (e.g. USER, PRODUCT, REFUND; nullable)
     * @param entityId   the ID of the entity the event refers to (nullable)
     * @param ipAddress  originating IP address (nullable)
     * @param userAgent  browser/client user-agent string (nullable)
     * @param details    free-form detail message (nullable)
     */
    void logEvent(String userId, String actorId, String eventType, String entityType, String entityId,
                  String ipAddress, String userAgent, String details);
}

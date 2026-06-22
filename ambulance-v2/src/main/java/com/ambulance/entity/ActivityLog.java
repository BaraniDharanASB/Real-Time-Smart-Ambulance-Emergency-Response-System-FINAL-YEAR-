package com.ambulance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private LogType type;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum LogType { INFO, SUCCESS, WARNING, DANGER }

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public ActivityLog() {}

    public ActivityLog(Long requestId, String actorName, String actorRole, String message, LogType type) {
        this.requestId = requestId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.message = message;
        this.type = type;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LogType getType() { return type; }
    public void setType(LogType type) { this.type = type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.ambulance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emergency_requests")
public class EmergencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ambulance_id")
    private Ambulance ambulance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "suggested_hospital_id")
    private Hospital suggestedHospital;

    @Column(name = "patient_latitude", nullable = false)
    private Double patientLatitude;

    @Column(name = "patient_longitude", nullable = false)
    private Double patientLongitude;

    @Column(name = "patient_address")
    private String patientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    private Double distance;
    private Integer eta;

    @Column(name = "is_panic")
    private boolean panic;

    // Comma-separated list of ambulance IDs sent request
    @Column(name = "sent_to_ambulance_ids", length = 500)
    private String sentToAmbulanceIds;

    // Comma-separated list of rejected ambulance IDs
    @Column(name = "rejected_ambulance_ids", length = 500)
    private String rejectedAmbulanceIds;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum Priority { HIGH, MEDIUM, LOW }

    public enum RequestStatus {
        REQUESTED, SENT_TO_DRIVERS, ACCEPTED, ARRIVING, ARRIVED, COMPLETED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        if (status == null) status = RequestStatus.REQUESTED;
        if (priority == null) priority = Priority.MEDIUM;
    }

    // ── Getters & Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    public Ambulance getAmbulance() { return ambulance; }
    public void setAmbulance(Ambulance ambulance) { this.ambulance = ambulance; }
    public Hospital getSuggestedHospital() { return suggestedHospital; }
    public void setSuggestedHospital(Hospital suggestedHospital) { this.suggestedHospital = suggestedHospital; }
    public Double getPatientLatitude() { return patientLatitude; }
    public void setPatientLatitude(Double patientLatitude) { this.patientLatitude = patientLatitude; }
    public Double getPatientLongitude() { return patientLongitude; }
    public void setPatientLongitude(Double patientLongitude) { this.patientLongitude = patientLongitude; }
    public String getPatientAddress() { return patientAddress; }
    public void setPatientAddress(String patientAddress) { this.patientAddress = patientAddress; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Integer getEta() { return eta; }
    public void setEta(Integer eta) { this.eta = eta; }
    public boolean isPanic() { return panic; }
    public void setPanic(boolean panic) { this.panic = panic; }
    public String getSentToAmbulanceIds() { return sentToAmbulanceIds; }
    public void setSentToAmbulanceIds(String sentToAmbulanceIds) { this.sentToAmbulanceIds = sentToAmbulanceIds; }
    public String getRejectedAmbulanceIds() { return rejectedAmbulanceIds; }
    public void setRejectedAmbulanceIds(String rejectedAmbulanceIds) { this.rejectedAmbulanceIds = rejectedAmbulanceIds; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}

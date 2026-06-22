package com.ambulance.repository;

import com.ambulance.entity.EmergencyRequest;
import com.ambulance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyRequestRepository extends JpaRepository<EmergencyRequest, Long> {
    List<EmergencyRequest> findByPatientOrderByRequestedAtDesc(User patient);
    List<EmergencyRequest> findAllByOrderByRequestedAtDesc();
    List<EmergencyRequest> findByStatusIn(List<EmergencyRequest.RequestStatus> statuses);

    @Query("SELECT r FROM EmergencyRequest r WHERE r.patient = :patient AND r.status NOT IN ('COMPLETED','CANCELLED') ORDER BY r.requestedAt DESC")
    Optional<EmergencyRequest> findActiveByPatient(@Param("patient") User patient);

    @Query("SELECT r FROM EmergencyRequest r WHERE r.ambulance.id = :ambId AND r.status NOT IN ('COMPLETED','CANCELLED') ORDER BY r.requestedAt DESC")
    Optional<EmergencyRequest> findActiveByAmbulanceId(@Param("ambId") Long ambId);

    @Query("SELECT r FROM EmergencyRequest r WHERE r.status = 'SENT_TO_DRIVERS' AND r.sentToAmbulanceIds LIKE %:ambId%")
    List<EmergencyRequest> findPendingRequestsForAmbulance(@Param("ambId") String ambId);
}

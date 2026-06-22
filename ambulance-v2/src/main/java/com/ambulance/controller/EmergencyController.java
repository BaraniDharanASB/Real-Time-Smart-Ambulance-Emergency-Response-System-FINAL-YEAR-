package com.ambulance.controller;

import com.ambulance.dto.EmergencyRequestDTO;
import com.ambulance.entity.EmergencyRequest;
import com.ambulance.entity.User;
import com.ambulance.service.AuthService;
import com.ambulance.service.EmergencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "*")
public class EmergencyController {

    @Autowired private EmergencyService emergencyService;
    @Autowired private AuthService authService;

    @PostMapping("/request/{patientId}")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable Long patientId, @RequestBody EmergencyRequestDTO dto) {
        return ResponseEntity.ok(emergencyService.createRequest(patientId, dto));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long requestId) {
        return ResponseEntity.ok(emergencyService.getRequestStatus(requestId));
    }

    @GetMapping("/patient/{patientId}/active")
    public ResponseEntity<Map<String, Object>> activeForPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(emergencyService.getActiveForPatient(patientId));
    }

    @GetMapping("/patient/{patientId}/history")
    public ResponseEntity<List<Map<String, Object>>> historyForPatient(@PathVariable Long patientId) {
        Optional<User> u = authService.findById(patientId);
        if (u.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(emergencyService.getByPatient(u.get()).stream().map(this::toListMap).toList());
    }

    @PutMapping("/request/{requestId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long requestId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(emergencyService.updateTripStatus(requestId, body.get("status")));
    }

    @GetMapping("/driver/{driverId}/pending")
    public ResponseEntity<Map<String, Object>> pendingForDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(emergencyService.getPendingForDriver(driverId));
    }

    @GetMapping("/driver/{driverId}/active")
    public ResponseEntity<Map<String, Object>> activeForDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(emergencyService.getActiveForDriver(driverId));
    }

    @PostMapping("/driver/{driverId}/accept/{requestId}")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable Long driverId, @PathVariable Long requestId) {
        return ResponseEntity.ok(emergencyService.acceptByDriverUserId(driverId, requestId));
    }

    @PostMapping("/driver/{driverId}/reject/{requestId}")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long driverId, @PathVariable Long requestId) {
        return ResponseEntity.ok(emergencyService.rejectByDriverUserId(driverId, requestId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(emergencyService.getAll().stream().map(this::toListMap).toList());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<Map<String, Object>>> getLogs() {
        return ResponseEntity.ok(emergencyService.getRecentLogs());
    }

    @GetMapping("/logs/{requestId}")
    public ResponseEntity<List<Map<String, Object>>> getLogsByRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(emergencyService.getLogsByRequest(requestId));
    }

    private Map<String, Object> toListMap(EmergencyRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("patientName", r.getPatient().getName());
        m.put("patientPhone", r.getPatient().getPhone() != null ? r.getPatient().getPhone() : "—");
        m.put("patientAddress", r.getPatientAddress());
        m.put("priority", r.getPriority().name());
        m.put("panic", r.isPanic());
        m.put("status", r.getStatus().name());
        m.put("distance", r.getDistance());
        m.put("eta", r.getEta());
        m.put("requestedAt", r.getRequestedAt() != null ? r.getRequestedAt().toString() : null);
        if (r.getAmbulance() != null) {
            m.put("vehicleNumber", r.getAmbulance().getVehicleNumber());
            if (r.getAmbulance().getDriver() != null)
                m.put("driverName", r.getAmbulance().getDriver().getName());
        }
        if (r.getSuggestedHospital() != null)
            m.put("hospitalName", r.getSuggestedHospital().getName());
        return m;
    }
}

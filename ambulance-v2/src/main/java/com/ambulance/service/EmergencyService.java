package com.ambulance.service;

import com.ambulance.dto.EmergencyRequestDTO;
import com.ambulance.entity.*;
import com.ambulance.entity.EmergencyRequest.Priority;
import com.ambulance.entity.EmergencyRequest.RequestStatus;
import com.ambulance.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmergencyService {

    @Autowired private EmergencyRequestRepository requestRepo;
    @Autowired private AmbulanceService ambulanceService;
    @Autowired private UserRepository userRepo;
    @Autowired private AmbulanceRepository ambulanceRepo;
    @Autowired private ActivityLogRepository logRepo;

    // ── Create Request ────────────────────────────────────────

    public Map<String, Object> createRequest(Long patientId, EmergencyRequestDTO dto) {
        User patient = userRepo.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        requestRepo.findActiveByPatient(patient).ifPresent(old -> {
            old.setStatus(RequestStatus.CANCELLED);
            requestRepo.save(old);
            log(old.getId(), patient.getName(), "PATIENT", "Previous request auto-cancelled.", ActivityLog.LogType.WARNING);
        });

        Priority priority = Priority.MEDIUM;
        try { priority = Priority.valueOf(dto.getPriority().toUpperCase()); } catch (Exception ignored) {}

        Optional<Hospital> hospital = ambulanceService.nearestHospital(dto.getLatitude(), dto.getLongitude());

        List<Ambulance> candidates = ambulanceService.findTopCandidates(
                dto.getLatitude(), dto.getLongitude(), priority.name());

        EmergencyRequest req = new EmergencyRequest();
        req.setPatient(patient);
        req.setPatientLatitude(dto.getLatitude());
        req.setPatientLongitude(dto.getLongitude());
        req.setPatientAddress(dto.getAddress() != null ? dto.getAddress() : "Detected location");
        req.setPriority(priority);
        req.setPanic(dto.isPanic());
        hospital.ifPresent(req::setSuggestedHospital);

        if (candidates.isEmpty()) {
            req.setStatus(RequestStatus.REQUESTED);
            requestRepo.save(req);
            log(req.getId(), patient.getName(), "SYSTEM", "No ambulances in range. Request queued.", ActivityLog.LogType.DANGER);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "No ambulances available within range. Request queued.");
            resp.put("requestId", req.getId());
            return resp;
        }

        String sentIds = candidates.stream().map(a -> String.valueOf(a.getId())).collect(Collectors.joining(","));
        req.setSentToAmbulanceIds(sentIds);
        req.setStatus(RequestStatus.SENT_TO_DRIVERS);

        Ambulance top = candidates.get(0);
        double dist = HaversineUtil.distance(dto.getLatitude(), dto.getLongitude(), top.getLatitude(), top.getLongitude());
        req.setDistance(Math.round(dist * 100.0) / 100.0);
        req.setEta(HaversineUtil.eta(dist));

        EmergencyRequest saved = requestRepo.save(req);

        String label = priority.name() + (dto.isPanic() ? " 🚨 PANIC" : "");
        log(saved.getId(), patient.getName(), "PATIENT",
                "Request created [" + label + "]. Sent to " + candidates.size() + " driver(s).", ActivityLog.LogType.INFO);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("requestId", saved.getId());
        resp.put("status", "SENT_TO_DRIVERS");
        resp.put("candidateCount", candidates.size());
        resp.put("priority", priority.name());
        resp.put("panic", dto.isPanic());
        resp.put("eta", req.getEta());
        resp.put("distance", req.getDistance());
        resp.put("sentToAmbulanceIds", sentIds);

        if (hospital.isPresent()) {
            Hospital h = hospital.get();
            resp.put("hospitalName", h.getName());
            resp.put("hospitalAddress", h.getAddress());
            resp.put("hospitalPhone", h.getPhone());
            double hd = HaversineUtil.distance(dto.getLatitude(), dto.getLongitude(), h.getLatitude(), h.getLongitude());
            resp.put("hospitalDistance", Math.round(hd * 100.0) / 100.0);
        }
        return resp;
    }

    // ── Accept / Reject by Driver User ID ────────────────────

    public Map<String, Object> acceptByDriverUserId(Long driverUserId, Long requestId) {
        Optional<User> userOpt = userRepo.findById(driverUserId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "Driver user not found");
        }
        Optional<Ambulance> ambOpt = ambulanceService.getByDriver(userOpt.get());
        if (ambOpt.isEmpty()) {
            return Map.of("success", false, "message", "No ambulance assigned to this driver");
        }
        return driverAccept(ambOpt.get().getId(), requestId);
    }

    public Map<String, Object> rejectByDriverUserId(Long driverUserId, Long requestId) {
        Optional<User> userOpt = userRepo.findById(driverUserId);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "Driver user not found");
        }
        Optional<Ambulance> ambOpt = ambulanceService.getByDriver(userOpt.get());
        if (ambOpt.isEmpty()) {
            return Map.of("success", false, "message", "No ambulance assigned to this driver");
        }
        return driverReject(ambOpt.get().getId(), requestId);
    }

    // ── Driver Accept ─────────────────────────────────────────

    public Map<String, Object> driverAccept(Long ambulanceId, Long requestId) {
        EmergencyRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() == RequestStatus.ACCEPTED) {
            return Map.of("success", false, "message", "Already accepted by another driver.");
        }
        if (req.getStatus() != RequestStatus.SENT_TO_DRIVERS) {
            return Map.of("success", false, "message", "Cannot accept. Status: " + req.getStatus());
        }

        Ambulance amb = ambulanceRepo.findById(ambulanceId)
                .orElseThrow(() -> new RuntimeException("Ambulance not found"));

        req.setAmbulance(amb);
        req.setStatus(RequestStatus.ACCEPTED);
        req.setAcceptedAt(LocalDateTime.now());

        double dist = HaversineUtil.distance(req.getPatientLatitude(), req.getPatientLongitude(),
                amb.getLatitude(), amb.getLongitude());
        req.setDistance(Math.round(dist * 100.0) / 100.0);
        req.setEta(HaversineUtil.eta(dist));

        amb.setStatus(Ambulance.AmbulanceStatus.BUSY);
        ambulanceRepo.save(amb);
        requestRepo.save(req);

        String driverName = amb.getDriver() != null ? amb.getDriver().getName() : "Driver";
        log(requestId, driverName, "DRIVER",
                "Accepted. Vehicle: " + amb.getVehicleNumber() + ", ETA: " + req.getEta() + " min.", ActivityLog.LogType.SUCCESS);

        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("message", "Request accepted!");
        r.put("requestId", requestId);
        r.put("status", "ACCEPTED");
        r.put("distance", req.getDistance());
        r.put("eta", req.getEta());
        return r;
    }

    // ── Driver Reject ─────────────────────────────────────────

    public Map<String, Object> driverReject(Long ambulanceId, Long requestId) {
        EmergencyRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.SENT_TO_DRIVERS) {
            return Map.of("success", false, "message", "Cannot reject. Status: " + req.getStatus());
        }

        String ambIdStr = String.valueOf(ambulanceId);
        String rejected = req.getRejectedAmbulanceIds();
        req.setRejectedAmbulanceIds(rejected == null || rejected.isEmpty() ? ambIdStr : rejected + "," + ambIdStr);

        Ambulance rejAmb = ambulanceRepo.findById(ambulanceId).orElse(null);
        String driverName = (rejAmb != null && rejAmb.getDriver() != null) ? rejAmb.getDriver().getName() : "Driver";
        log(requestId, driverName, "DRIVER",
                "Rejected the request. Vehicle: " + (rejAmb != null ? rejAmb.getVehicleNumber() : "N/A"), ActivityLog.LogType.WARNING);

        List<String> sentIds = parseCsv(req.getSentToAmbulanceIds());
        List<String> rejectedIds = parseCsv(req.getRejectedAmbulanceIds());
        List<String> remaining = sentIds.stream().filter(id -> !rejectedIds.contains(id)).collect(Collectors.toList());

        if (remaining.isEmpty()) {
            List<Ambulance> newCandidates = ambulanceService.findTopCandidates(
                    req.getPatientLatitude(), req.getPatientLongitude(), req.getPriority().name());
            List<String> newIds = newCandidates.stream()
                    .map(a -> String.valueOf(a.getId()))
                    .filter(id -> !rejectedIds.contains(id))
                    .collect(Collectors.toList());

            if (newIds.isEmpty()) {
                req.setStatus(RequestStatus.CANCELLED);
                requestRepo.save(req);
                log(requestId, "SYSTEM", "SYSTEM", "All drivers rejected. Request cancelled.", ActivityLog.LogType.DANGER);
                return Map.of("success", true, "reassigned", false, "message", "All drivers rejected. Cancelled.");
            }
            req.setSentToAmbulanceIds(String.join(",", newIds));
            log(requestId, "SYSTEM", "SYSTEM", "Reassigned to " + newIds.size() + " new driver(s).", ActivityLog.LogType.INFO);
        }

        requestRepo.save(req);
        return Map.of("success", true, "reassigned", !remaining.isEmpty(), "message", "Rejected");
    }

    // ── Trip Status Update ────────────────────────────────────

    public Map<String, Object> updateTripStatus(Long requestId, String newStatus) {
        EmergencyRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        RequestStatus status;
        try { status = RequestStatus.valueOf(newStatus.toUpperCase()); }
        catch (Exception e) { return Map.of("success", false, "message", "Invalid status: " + newStatus); }

        req.setStatus(status);
        String actor = (req.getAmbulance() != null && req.getAmbulance().getDriver() != null)
                ? req.getAmbulance().getDriver().getName() : "Driver";

        if (status == RequestStatus.COMPLETED || status == RequestStatus.CANCELLED) {
            req.setCompletedAt(LocalDateTime.now());
            if (req.getAmbulance() != null) {
                req.getAmbulance().setStatus(Ambulance.AmbulanceStatus.AVAILABLE);
                ambulanceRepo.save(req.getAmbulance());
            }
        }
        requestRepo.save(req);
        log(requestId, actor, "DRIVER", "Status updated → " + status, ActivityLog.LogType.INFO);
        return Map.of("success", true, "status", status.name());
    }

    // ── Polling / Status ──────────────────────────────────────

    public Map<String, Object> getRequestStatus(Long requestId) {
        return requestRepo.findById(requestId)
                .map(this::toDetailMap)
                .orElse(Map.of("success", false, "message", "Not found"));
    }

    public Map<String, Object> getActiveForPatient(Long patientId) {
        return userRepo.findById(patientId)
                .flatMap(u -> requestRepo.findActiveByPatient(u))
                .map(this::toDetailMap)
                .orElse(Map.of("success", false, "message", "No active request"));
    }

    public Map<String, Object> getPendingForDriver(Long driverId) {
        Optional<Ambulance> ambOpt = userRepo.findById(driverId)
                .flatMap(u -> ambulanceService.getByDriver(u));
        if (ambOpt.isEmpty()) return Map.of("success", false, "message", "No ambulance assigned");

        String ambIdStr = String.valueOf(ambOpt.get().getId());
        List<EmergencyRequest> pending = requestRepo.findPendingRequestsForAmbulance(ambIdStr);
        List<String> rejectedIds = new ArrayList<>();

        Optional<EmergencyRequest> eligible = pending.stream()
                .filter(r -> !parseCsv(r.getRejectedAmbulanceIds()).contains(ambIdStr))
                .findFirst();

        if (eligible.isEmpty()) return Map.of("success", false, "message", "No pending requests");

        Map<String, Object> r = toDetailMap(eligible.get());
        r.put("ambulanceId", ambOpt.get().getId());
        return r;
    }

    public Map<String, Object> getActiveForDriver(Long driverId) {
        Optional<Ambulance> ambOpt = userRepo.findById(driverId)
                .flatMap(u -> ambulanceService.getByDriver(u));
        if (ambOpt.isEmpty()) return Map.of("success", false, "message", "No ambulance assigned");

        return requestRepo.findActiveByAmbulanceId(ambOpt.get().getId())
                .map(req -> {
                    Map<String, Object> r = toDetailMap(req);
                    r.put("ambulanceId", ambOpt.get().getId());
                    return r;
                })
                .orElse(Map.of("success", false, "message", "No active assignment"));
    }

    public List<EmergencyRequest> getAll() { return requestRepo.findAllByOrderByRequestedAtDesc(); }

    public List<EmergencyRequest> getByPatient(User patient) {
        return requestRepo.findByPatientOrderByRequestedAtDesc(patient);
    }

    // ── Activity Logs ─────────────────────────────────────────

    public List<Map<String, Object>> getRecentLogs() {
        return logRepo.findTop50ByOrderByCreatedAtDesc().stream().map(this::logToMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLogsByRequest(Long requestId) {
        return logRepo.findByRequestIdOrderByCreatedAtDesc(requestId).stream().map(this::logToMap).collect(Collectors.toList());
    }

    // ── Timeout Scheduler ─────────────────────────────────────

    @Scheduled(fixedDelay = 30000)
    public void checkTimeouts() {
        List<EmergencyRequest> pending = requestRepo.findByStatusIn(List.of(RequestStatus.SENT_TO_DRIVERS));
        for (EmergencyRequest req : pending) {
            if (req.getRequestedAt() != null && req.getRequestedAt().isBefore(LocalDateTime.now().minusSeconds(60))) {
                List<Ambulance> newCandidates = ambulanceService.findTopCandidates(
                        req.getPatientLatitude(), req.getPatientLongitude(), req.getPriority().name());
                if (!newCandidates.isEmpty()) {
                    String newIds = newCandidates.stream().map(a -> String.valueOf(a.getId())).collect(Collectors.joining(","));
                    req.setSentToAmbulanceIds(newIds);
                    req.setRequestedAt(LocalDateTime.now());
                    requestRepo.save(req);
                    log(req.getId(), "SYSTEM", "SYSTEM", "Timeout: Auto-reassigned to " + newCandidates.size() + " driver(s).", ActivityLog.LogType.WARNING);
                } else {
                    req.setStatus(RequestStatus.CANCELLED);
                    requestRepo.save(req);
                    log(req.getId(), "SYSTEM", "SYSTEM", "Timeout: No drivers found. Cancelled.", ActivityLog.LogType.DANGER);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private Map<String, Object> toDetailMap(EmergencyRequest req) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("requestId", req.getId());
        m.put("status", req.getStatus().name());
        m.put("priority", req.getPriority().name());
        m.put("panic", req.isPanic());
        m.put("patientName", req.getPatient().getName());
        m.put("patientPhone", req.getPatient().getPhone() != null ? req.getPatient().getPhone() : "—");
        m.put("patientLat", req.getPatientLatitude());
        m.put("patientLon", req.getPatientLongitude());
        m.put("patientAddress", req.getPatientAddress());
        m.put("distance", req.getDistance());
        m.put("eta", req.getEta());
        m.put("requestedAt", req.getRequestedAt() != null ? req.getRequestedAt().toString() : null);
        m.put("acceptedAt", req.getAcceptedAt() != null ? req.getAcceptedAt().toString() : null);
        m.put("sentToAmbulanceIds", req.getSentToAmbulanceIds());

        if (req.getAmbulance() != null) {
            Ambulance a = req.getAmbulance();
            m.put("ambulanceId", a.getId());
            m.put("vehicleNumber", a.getVehicleNumber());
            m.put("ambulanceLat", a.getLatitude());
            m.put("ambulanceLon", a.getLongitude());
            if (a.getDriver() != null) {
                m.put("driverName", a.getDriver().getName());
                m.put("driverPhone", a.getDriver().getPhone() != null ? a.getDriver().getPhone() : "—");
                m.put("driverId", a.getDriver().getId());
            }
        }

        if (req.getSuggestedHospital() != null) {
            Hospital h = req.getSuggestedHospital();
            m.put("hospitalName", h.getName());
            m.put("hospitalAddress", h.getAddress());
            m.put("hospitalPhone", h.getPhone());
            m.put("hospitalLat", h.getLatitude());
            m.put("hospitalLon", h.getLongitude());
            m.put("hospitalSpeciality", h.getSpeciality());
            if (req.getPatientLatitude() != null && h.getLatitude() != null) {
                double hd = HaversineUtil.distance(req.getPatientLatitude(), req.getPatientLongitude(), h.getLatitude(), h.getLongitude());
                m.put("hospitalDistance", Math.round(hd * 100.0) / 100.0);
            }
        }
        return m;
    }

    private Map<String, Object> logToMap(ActivityLog l) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("requestId", l.getRequestId());
        m.put("actorName", l.getActorName());
        m.put("actorRole", l.getActorRole());
        m.put("message", l.getMessage());
        m.put("type", l.getType() != null ? l.getType().name() : "INFO");
        m.put("createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
        return m;
    }

    private void log(Long requestId, String actor, String role, String msg, ActivityLog.LogType type) {
        logRepo.save(new ActivityLog(requestId, actor, role, msg, type));
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}

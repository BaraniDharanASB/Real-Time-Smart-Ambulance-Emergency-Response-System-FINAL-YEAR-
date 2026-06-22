package com.ambulance.service;

import com.ambulance.entity.Ambulance;
import com.ambulance.entity.Hospital;
import com.ambulance.entity.User;
import com.ambulance.repository.AmbulanceRepository;
import com.ambulance.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AmbulanceService {

    @Autowired private AmbulanceRepository ambulanceRepository;
    @Autowired private HospitalRepository hospitalRepository;

    @Value("${app.dispatch.radius-km:10.0}")
    private double radiusKm;

    @Value("${app.dispatch.top-drivers:3}")
    private int topDrivers;

    // ── Core Queries ──────────────────────────────────────────

    public List<Ambulance> getAll() { return ambulanceRepository.findAll(); }

    public Optional<Ambulance> getById(Long id) { return ambulanceRepository.findById(id); }

    public Optional<Ambulance> getByDriver(User driver) { return ambulanceRepository.findByDriver(driver); }

    public Ambulance save(Ambulance a) { return ambulanceRepository.save(a); }

    public Map<String, Long> statusCounts() {
        Map<String, Long> m = new HashMap<>();
        m.put("AVAILABLE", ambulanceRepository.countByStatus(Ambulance.AmbulanceStatus.AVAILABLE));
        m.put("BUSY",      ambulanceRepository.countByStatus(Ambulance.AmbulanceStatus.BUSY));
        m.put("OFFLINE",   ambulanceRepository.countByStatus(Ambulance.AmbulanceStatus.OFFLINE));
        m.put("TOTAL",     ambulanceRepository.count());
        return m;
    }

    // ── Dispatch: Top-N nearest scored ambulances ─────────────

    /**
     * Returns top-N available ambulances within radiusKm, sorted by dispatch score.
     * score = distance * priorityWeight  (lower = better)
     */
    public List<Ambulance> findTopCandidates(double patLat, double patLon, String priority) {
        List<Ambulance> available = ambulanceRepository.findByStatus(Ambulance.AmbulanceStatus.AVAILABLE);

        return available.stream()
                .filter(a -> a.getLatitude() != null && a.getLongitude() != null)
                .filter(a -> {
                    double d = HaversineUtil.distance(patLat, patLon, a.getLatitude(), a.getLongitude());
                    return d <= radiusKm;
                })
                .sorted(Comparator.comparingDouble(a ->
                        HaversineUtil.score(
                                HaversineUtil.distance(patLat, patLon, a.getLatitude(), a.getLongitude()),
                                priority)))
                .limit(topDrivers)
                .collect(Collectors.toList());
    }

    // ── Hospital suggestion ───────────────────────────────────

    public Optional<Hospital> nearestHospital(double lat, double lon) {
        List<Hospital> hospitals = hospitalRepository.findAll();
        return hospitals.stream()
                .filter(h -> h.getLatitude() != null && h.getLongitude() != null)
                .min(Comparator.comparingDouble(h ->
                        HaversineUtil.distance(lat, lon, h.getLatitude(), h.getLongitude())));
    }

    // ── Status / Location updates ─────────────────────────────

    public Ambulance updateStatus(Long id, String status) {
        Ambulance a = ambulanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ambulance not found: " + id));
        a.setStatus(Ambulance.AmbulanceStatus.valueOf(status.toUpperCase()));
        return ambulanceRepository.save(a);
    }

    public Ambulance updateLocation(Long id, double lat, double lon) {
        Ambulance a = ambulanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ambulance not found: " + id));
        a.setLatitude(lat);
        a.setLongitude(lon);
        return ambulanceRepository.save(a);
    }

    // ── Nearest ambulances for patient preview (top-3 with distance) ──

    public List<Map<String, Object>> nearestAmbulancesPreview(double patLat, double patLon, String priority) {
        List<Ambulance> candidates = findTopCandidates(patLat, patLon, priority);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Ambulance a : candidates) {
            double dist = HaversineUtil.distance(patLat, patLon, a.getLatitude(), a.getLongitude());
            int eta = HaversineUtil.eta(dist);
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("vehicleNumber", a.getVehicleNumber());
            m.put("driverName", a.getDriver() != null ? a.getDriver().getName() : "N/A");
            m.put("latitude", a.getLatitude());
            m.put("longitude", a.getLongitude());
            m.put("distance", Math.round(dist * 100.0) / 100.0);
            m.put("eta", eta);
            m.put("score", Math.round(HaversineUtil.score(dist, priority) * 100.0) / 100.0);
            result.add(m);
        }
        return result;
    }
}

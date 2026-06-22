package com.ambulance.controller;

import com.ambulance.entity.Ambulance;
import com.ambulance.entity.User;
import com.ambulance.service.AmbulanceService;
import com.ambulance.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ambulances")
@CrossOrigin(origins = "*")
public class AmbulanceController {

    @Autowired private AmbulanceService ambulanceService;
    @Autowired private AuthService authService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        return ResponseEntity.ok(ambulanceService.getAll().stream().map(this::toMap).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(ambulanceService.statusCounts());
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<Map<String, Object>> getByDriver(@PathVariable Long driverId) {
        Optional<User> u = authService.findById(driverId);
        if (u.isEmpty()) return ResponseEntity.notFound().build();
        return ambulanceService.getByDriver(u.get())
                .map(a -> ResponseEntity.ok(toMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nearest")
    public ResponseEntity<List<Map<String, Object>>> nearest(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "MEDIUM") String priority) {
        return ResponseEntity.ok(ambulanceService.nearestAmbulancesPreview(lat, lon, priority));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Ambulance a = ambulanceService.updateStatus(id, body.get("status"));
            Map<String, Object> r = new HashMap<>();
            r.put("success", true); r.put("ambulance", toMap(a));
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false); r.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(r);
        }
    }

    @PutMapping("/{id}/location")
    public ResponseEntity<Map<String, Object>> updateLocation(
            @PathVariable Long id, @RequestBody Map<String, Double> body) {
        try {
            Ambulance a = ambulanceService.updateLocation(id, body.get("latitude"), body.get("longitude"));
            Map<String, Object> r = new HashMap<>();
            r.put("success", true); r.put("ambulance", toMap(a));
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false); r.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(r);
        }
    }

    private Map<String, Object> toMap(Ambulance a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("vehicleNumber", a.getVehicleNumber());
        m.put("status", a.getStatus().name());
        m.put("latitude", a.getLatitude());
        m.put("longitude", a.getLongitude());
        m.put("lastUpdated", a.getLastUpdated() != null ? a.getLastUpdated().toString() : null);
        if (a.getDriver() != null) {
            m.put("driverId", a.getDriver().getId());
            m.put("driverName", a.getDriver().getName());
            m.put("driverPhone", a.getDriver().getPhone());
            m.put("driverEmail", a.getDriver().getEmail());
        }
        return m;
    }
}

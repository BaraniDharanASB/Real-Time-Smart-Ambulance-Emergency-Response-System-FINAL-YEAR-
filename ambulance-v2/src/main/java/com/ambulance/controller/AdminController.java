package com.ambulance.controller;

import com.ambulance.entity.Hospital;
import com.ambulance.entity.User;
import com.ambulance.repository.HospitalRepository;
import com.ambulance.repository.UserRepository;
import com.ambulance.service.AmbulanceService;
import com.ambulance.service.EmergencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired private UserRepository userRepo;
    @Autowired private AmbulanceService ambulanceService;
    @Autowired private EmergencyService emergencyService;
    @Autowired private HospitalRepository hospitalRepo;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> d = new HashMap<>();
        d.put("totalUsers",    userRepo.count());
        d.put("totalPatients", userRepo.findByRole(User.Role.PATIENT).size());
        d.put("totalDrivers",  userRepo.findByRole(User.Role.DRIVER).size());
        d.put("totalRequests", emergencyService.getAll().size());
        d.put("ambulanceStats", ambulanceService.statusCounts());
        return ResponseEntity.ok(d);
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        return ResponseEntity.ok(userRepo.findAll().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            m.put("role", u.getRole().name());
            m.put("phone", u.getPhone() != null ? u.getPhone() : "—");
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            return m;
        }).toList());
    }

    @GetMapping("/drivers")
    public ResponseEntity<List<Map<String, Object>>> drivers() {
        return ResponseEntity.ok(userRepo.findByRole(User.Role.DRIVER).stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone() != null ? u.getPhone() : "—");
            ambulanceService.getByDriver(u).ifPresent(a -> {
                m.put("vehicleNumber", a.getVehicleNumber());
                m.put("ambulanceStatus", a.getStatus().name());
                m.put("ambulanceId", a.getId());
                m.put("latitude", a.getLatitude());
                m.put("longitude", a.getLongitude());
            });
            return m;
        }).toList());
    }

    @GetMapping("/hospitals")
    public ResponseEntity<List<Map<String, Object>>> hospitals() {
        return ResponseEntity.ok(hospitalRepo.findAll().stream().map(h -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("name", h.getName());
            m.put("address", h.getAddress());
            m.put("phone", h.getPhone());
            m.put("speciality", h.getSpeciality());
            m.put("latitude", h.getLatitude());
            m.put("longitude", h.getLongitude());
            return m;
        }).toList());
    }
}

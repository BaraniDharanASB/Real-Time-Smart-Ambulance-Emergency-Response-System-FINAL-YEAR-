package com.ambulance.config;

import com.ambulance.entity.*;
import com.ambulance.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserRepository userRepo;
    @Autowired private AmbulanceRepository ambulanceRepo;
    @Autowired private HospitalRepository hospitalRepo;
    @Autowired private ActivityLogRepository logRepo;
    @Autowired private PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) return;

        // ── Admin ──
        User admin = user("System Admin", "admin@ambulance.com", "admin123", User.Role.ADMIN, "9999999999");

        // ── Drivers ──
        User d1 = user("Rajan Kumar",   "driver1@ambulance.com", "driver123", User.Role.DRIVER, "9876543210");
        User d2 = user("Suresh Babu",   "driver2@ambulance.com", "driver123", User.Role.DRIVER, "9876543211");
        User d3 = user("Anand Raj",     "driver3@ambulance.com", "driver123", User.Role.DRIVER, "9876543212");
        User d4 = user("Vijay Singh",   "driver4@ambulance.com", "driver123", User.Role.DRIVER, "9876543213");
        User d5 = user("Muthu Selvam",  "driver5@ambulance.com", "driver123", User.Role.DRIVER, "9876543214");

        // ── Patients ──
        user("Priya Sharma",  "patient1@ambulance.com", "patient123", User.Role.PATIENT, "9123456789");
        user("Amit Patel",    "patient2@ambulance.com", "patient123", User.Role.PATIENT, "9123456788");
        user("Kavya Menon",   "patient3@ambulance.com", "patient123", User.Role.PATIENT, "9123456787");

        // ── Ambulances (Salem, TN area coordinates) ──
        ambulance("TN33A1001", d1, 11.6643, 78.1460, Ambulance.AmbulanceStatus.AVAILABLE);
        ambulance("TN33A1002", d2, 11.6800, 78.1200, Ambulance.AmbulanceStatus.AVAILABLE);
        ambulance("TN33A1003", d3, 11.6500, 78.1600, Ambulance.AmbulanceStatus.AVAILABLE);
        ambulance("TN33A1004", d4, 11.6900, 78.1800, Ambulance.AmbulanceStatus.OFFLINE);
        ambulance("TN33A1005", d5, 11.6400, 78.1300, Ambulance.AmbulanceStatus.AVAILABLE);

        // ── Hospitals ──
        hospital("Salem Government Hospital",  "Saradha College Road, Salem",           11.6576, 78.1534, "0427-2411001", "General & Trauma");
        hospital("Apollo Speciality Hospital", "No.23, Junction Main Road, Salem",       11.6643, 78.1456, "0427-6690000", "Multi-Specialty");
        hospital("Vinayaka Mission Hospital",  "NH-7, Sankagiri Main Road, Salem",       11.6220, 78.1990, "0427-4064444", "Cardiology");
        hospital("Sri Ramakrishna Hospital",   "Trichy Road, Salem",                     11.6380, 78.1610, "0427-2220001", "Orthopaedics");
        hospital("Kaveri Hospital",            "Mettur Road, Omalur, Salem",             11.7150, 78.0750, "0427-2480001", "Emergency & ICU");

        // ── Seed activity logs ──
        logRepo.save(new ActivityLog(null, "System", "SYSTEM", "Smart Ambulance System initialized and ready.", ActivityLog.LogType.SUCCESS));
        logRepo.save(new ActivityLog(null, "System", "SYSTEM", "5 ambulances registered in Salem region.", ActivityLog.LogType.INFO));
        logRepo.save(new ActivityLog(null, "System", "SYSTEM", "5 hospitals loaded for suggestion engine.", ActivityLog.LogType.INFO));

        System.out.println("=================================================");
        System.out.println("✅  Demo data seeded successfully!");
        System.out.println("   Admin:   admin@ambulance.com    / admin123");
        System.out.println("   Driver:  driver1@ambulance.com  / driver123");
        System.out.println("   Patient: patient1@ambulance.com / patient123");
        System.out.println("=================================================");
    }

    private User user(String name, String email, String pwd, User.Role role, String phone) {
        User u = new User();
        u.setName(name); u.setEmail(email);
        u.setPassword(encoder.encode(pwd));
        u.setRole(role); u.setPhone(phone);
        return userRepo.save(u);
    }

    private void ambulance(String vn, User driver, double lat, double lon, Ambulance.AmbulanceStatus status) {
        Ambulance a = new Ambulance();
        a.setVehicleNumber(vn); a.setDriver(driver);
        a.setLatitude(lat); a.setLongitude(lon);
        a.setStatus(status);
        ambulanceRepo.save(a);
    }

    private void hospital(String name, String addr, double lat, double lon, String phone, String spec) {
        Hospital h = new Hospital();
        h.setName(name); h.setAddress(addr);
        h.setLatitude(lat); h.setLongitude(lon);
        h.setPhone(phone); h.setSpeciality(spec);
        hospitalRepo.save(h);
    }
}

package com.ambulance.service;

import com.ambulance.dto.AuthResponse;
import com.ambulance.dto.LoginRequest;
import com.ambulance.dto.RegisterRequest;
import com.ambulance.entity.User;
import com.ambulance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            return new AuthResponse(false, "Email already registered.");

        User.Role role;
        try {
            role = User.Role.valueOf(req.getRole().toUpperCase());
            if (role == User.Role.ADMIN) return new AuthResponse(false, "Cannot self-register as ADMIN.");
        } catch (Exception e) {
            role = User.Role.PATIENT;
        }

        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setPhone(req.getPhone());
        u.setRole(role);
        userRepository.save(u);
        return new AuthResponse(true, "Registration successful! Please login.");
    }

    public AuthResponse login(LoginRequest req) {
        Optional<User> opt = userRepository.findByEmail(req.getEmail());
        if (opt.isEmpty()) return new AuthResponse(false, "Invalid email or password.");
        User u = opt.get();
        if (!passwordEncoder.matches(req.getPassword(), u.getPassword()))
            return new AuthResponse(false, "Invalid email or password.");
        return new AuthResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }

    public Optional<User> findById(Long id) { return userRepository.findById(id); }
}

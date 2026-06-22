package com.ambulance.dto;

public class AuthResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String message;
    private boolean success;

    public AuthResponse() {}
    public AuthResponse(boolean success, String message) { this.success = success; this.message = message; }
    public AuthResponse(Long id, String name, String email, String role) {
        this.id = id; this.name = name; this.email = email; this.role = role;
        this.success = true; this.message = "Login successful";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}

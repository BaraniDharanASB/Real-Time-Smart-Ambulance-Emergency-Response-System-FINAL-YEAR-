package com.ambulance.dto;

public class EmergencyRequestDTO {
    private Double latitude;
    private Double longitude;
    private String address;
    private String priority;
    private boolean panic;

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public boolean isPanic() { return panic; }
    public void setPanic(boolean panic) { this.panic = panic; }
}

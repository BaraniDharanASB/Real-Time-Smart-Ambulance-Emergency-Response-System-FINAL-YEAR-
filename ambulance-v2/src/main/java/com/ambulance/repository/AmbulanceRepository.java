package com.ambulance.repository;

import com.ambulance.entity.Ambulance;
import com.ambulance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AmbulanceRepository extends JpaRepository<Ambulance, Long> {
    List<Ambulance> findByStatus(Ambulance.AmbulanceStatus status);
    Optional<Ambulance> findByDriver(User driver);
    long countByStatus(Ambulance.AmbulanceStatus status);
}

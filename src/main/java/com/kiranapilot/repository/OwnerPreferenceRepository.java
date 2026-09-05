package com.kiranapilot.repository;

import com.kiranapilot.entity.OwnerPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OwnerPreferenceRepository extends JpaRepository<OwnerPreference, String> {
}

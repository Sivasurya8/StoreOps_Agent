package com.kiranapilot.memory;

import com.kiranapilot.entity.OwnerPreference;
import com.kiranapilot.repository.OwnerPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerPreferenceService {

    private final OwnerPreferenceRepository preferenceRepository;

    @Value("${kiranapilot.store.name:Sri Lakshmi Supermarket}")
    private String defaultStoreName;

    @Value("${kiranapilot.store.gstin:29AAAPL1234C1ZV}")
    private String defaultStoreGstin;

    @Value("${kiranapilot.store.address:124 Main Bazaar, Bangalore, Karnataka - 560001}")
    private String defaultStoreAddress;

    @Value("${kiranapilot.store.phone:+91 98765 43210}")
    private String defaultStorePhone;

    @Transactional
    public void setPreference(String key, String value) {
        String cleanKey = key.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        OwnerPreference pref = preferenceRepository.findById(cleanKey)
                .orElse(OwnerPreference.builder().key(cleanKey).build());
        pref.setValue(value.trim());
        preferenceRepository.save(pref);
        log.info("Saved owner preference: {} = {}", cleanKey, value);
    }

    @Transactional(readOnly = true)
    public String getPreference(String key, String defaultValue) {
        String cleanKey = key.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return preferenceRepository.findById(cleanKey)
                .map(OwnerPreference::getValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllPreferences() {
        return preferenceRepository.findAll().stream()
                .collect(Collectors.toMap(OwnerPreference::getKey, OwnerPreference::getValue));
    }

    public String getStoreName() {
        return getPreference("store_name", defaultStoreName);
    }

    public String getStoreGstin() {
        return getPreference("store_gstin", defaultStoreGstin);
    }

    public String getStoreAddress() {
        return getPreference("store_address", defaultStoreAddress);
    }

    public String getStorePhone() {
        return getPreference("store_phone", defaultStorePhone);
    }

    public String getDefaultPaymentMode() {
        return getPreference("default_payment_mode", "UPI");
    }
}

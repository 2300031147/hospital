package com.aerovhyn.api.service;

import com.aerovhyn.domain.entity.*;
import com.aerovhyn.domain.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseSeederService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeederService.class);

    private final UserRepository userRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final HospitalRepository hospitalRepository;
    private final HistoricalPatternRepository historicalPatternRepository;
    private final LogRepository logRepository;
    private final BlockchainRepository blockchainRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public DatabaseSeederService(
            UserRepository userRepository,
            AmbulanceRepository ambulanceRepository,
            HospitalRepository hospitalRepository,
            HistoricalPatternRepository historicalPatternRepository,
            LogRepository logRepository,
            BlockchainRepository blockchainRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.ambulanceRepository = ambulanceRepository;
        this.hospitalRepository = hospitalRepository;
        this.historicalPatternRepository = historicalPatternRepository;
        this.logRepository = logRepository;
        this.blockchainRepository = blockchainRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public void seed() {
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping seeder.");
            return;
        }

        log.info("Seeding initial database state...");

        // 1. Seed Ambulances
        AmbulanceEntity amb1 = ambulanceRepository.save(new AmbulanceEntity("AMB-001", 17.4239, 78.4483));
        AmbulanceEntity amb2 = ambulanceRepository.save(new AmbulanceEntity("AMB-002", 17.4156, 78.4347));
        AmbulanceEntity amb3 = ambulanceRepository.save(new AmbulanceEntity("AMB-003", 17.4401, 78.4983));

        // 2. Seed Hospitals
        HospitalEntity apollo = createHospital("Apollo Emergency Hospital", 17.4239, 78.4483, 8, 12, 5, 8, List.of("cardiology", "neurology", "trauma"), 45, 120, 0.95);
        HospitalEntity kims = createHospital("KIMS Heart Center", 17.4156, 78.4347, 6, 10, 4, 6, List.of("cardiology", "pulmonology"), 62, 100, 0.90);
        HospitalEntity yashoda = createHospital("Yashoda Super Specialty", 17.4401, 78.4983, 10, 15, 7, 10, List.of("cardiology", "orthopedics", "neurology", "trauma"), 30, 150, 0.92);
        HospitalEntity care = createHospital("Care Hospitals", 17.4485, 78.3908, 4, 8, 3, 5, List.of("trauma", "orthopedics"), 78, 90, 0.85);
        HospitalEntity continental = createHospital("Continental General Hospital", 17.4350, 78.4600, 3, 6, 2, 4, List.of("general", "pulmonology"), 55, 80, 0.78);
        HospitalEntity sunshine = createHospital("Sunshine Trauma Center", 17.4100, 78.4750, 7, 10, 5, 7, List.of("trauma", "neurology", "orthopedics"), 40, 110, 0.88);
        HospitalEntity medicover = createHospital("Medicover Emergency Wing", 17.4600, 78.4200, 5, 8, 3, 5, List.of("cardiology", "general"), 70, 95, 0.82);
        HospitalEntity global = createHospital("Global Hospitals", 17.4000, 78.4400, 12, 18, 9, 12, List.of("cardiology", "neurology", "trauma", "pulmonology", "orthopedics"), 25, 200, 0.97);

        // 3. Seed Users
        createUser("admin", "admin123", "System Administrator", "command_center", null, null);
        createUser("hosp1", "hosp123", "Apollo Admin", "hospital_admin", null, apollo.getId());
        createUser("hosp2", "hosp123", "KIMS Admin", "hospital_admin", null, kims.getId());
        createUser("paramedic1", "rescue123", "Ravi Kumar", "paramedic", amb1.getId(), null);
        createUser("driver1", "drive123", "Suresh Reddy", "paramedic", amb2.getId(), null);
        createUser("medic01", "medic123", "Priya Sharma", "paramedic", amb3.getId(), null);
        createUser("dispatcher1", "dispatch123", "Dispatch Operator", "dispatcher", null, null);

        // 4. Seed Settings if empty
        // Seeding is already baseline in Flyway (V3), but let's verify settings count
        log.info("Database seeding completed successfully.");
    }

    @Transactional
    public void wipeAndReseed() {
        log.info("Performing full database reset and wipe...");

        // Delete in order to avoid constraint violations
        historicalPatternRepository.deleteAllInBatch();
        logRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ambulanceRepository.deleteAllInBatch();
        hospitalRepository.deleteAllInBatch();
        blockchainRepository.deleteAllInBatch();

        // Reset sequences so IDs start from 1 again
        entityManager.createNativeQuery("ALTER SEQUENCE hospitals_id_seq RESTART WITH 1").executeUpdate();
        entityManager.createNativeQuery("ALTER SEQUENCE ambulances_id_seq RESTART WITH 1").executeUpdate();
        entityManager.createNativeQuery("ALTER SEQUENCE users_id_seq RESTART WITH 1").executeUpdate();
        entityManager.createNativeQuery("ALTER SEQUENCE logs_id_seq RESTART WITH 1").executeUpdate();
        entityManager.createNativeQuery("ALTER SEQUENCE historical_patterns_id_seq RESTART WITH 1").executeUpdate();
        entityManager.flush();

        // Seed clean records
        seed();
    }

    private HospitalEntity createHospital(
            String name, double lat, double lon, int icuBeds, int totalIcuBeds,
            int ventilators, int totalVentilators, List<String> specialists,
            int currentLoad, int maxCapacity, double equipmentScore) {

        HospitalEntity entity = new HospitalEntity(name, lat, lon);
        entity.setIcuBeds(icuBeds);
        entity.setTotalIcuBeds(totalIcuBeds);
        entity.setVentilators(ventilators);
        entity.setTotalVentilators(totalVentilators);
        try {
            entity.setSpecialists(objectMapper.writeValueAsString(specialists));
        } catch (Exception e) {
            entity.setSpecialists("[]");
        }
        entity.setCurrentLoad(currentLoad);
        entity.setMaxCapacity(maxCapacity);
        entity.setEquipmentScore(equipmentScore);
        entity.setStatus("active");
        entity.setLastUpdated(LocalDateTime.now());
        HospitalEntity saved = hospitalRepository.save(entity);

        // Seed 24x7 historical patterns for this hospital
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                double baseLoad = 0.6;
                if (hour >= 18 && hour <= 23) baseLoad += 0.2;
                if (day >= 5) baseLoad += 0.1;
                historicalPatternRepository.save(
                        new HistoricalPatternEntity(saved.getId(), day, hour, Math.min(baseLoad, 1.0))
                );
            }
        }
        return saved;
    }

    private void createUser(String username, String password, String fullName, String role, Long ambulanceId, Long hospitalId) {
        UserEntity entity = new UserEntity(username, passwordEncoder.encode(password), fullName, role);
        entity.setAmbulanceId(ambulanceId);
        entity.setHospitalId(hospitalId);
        userRepository.save(entity);
    }
}

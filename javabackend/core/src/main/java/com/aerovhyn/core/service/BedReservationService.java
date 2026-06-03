package com.aerovhyn.core.service;

public interface BedReservationService {
    boolean softReserve(Long hospitalId, Long ambulanceId);
    boolean release(Long hospitalId, Long ambulanceId);
    void cleanupStaleReservations();
}

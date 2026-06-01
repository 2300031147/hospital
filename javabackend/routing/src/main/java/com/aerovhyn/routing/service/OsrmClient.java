package com.aerovhyn.routing.service;

import java.util.List;
import java.util.Map;

public interface OsrmClient {
    double getEtaMinutes(double fromLat, double fromLon, double toLat, double toLon);
    double getDistanceKm(double fromLat, double fromLon, double toLat, double toLon);

    Map<String, RouteInfo> getBatchRoutes(double fromLat, double fromLon, List<HospitalLocation> targets);

    record HospitalLocation(String id, double lat, double lon) {}
    record RouteInfo(double distanceKm, double durationMin) {}
}

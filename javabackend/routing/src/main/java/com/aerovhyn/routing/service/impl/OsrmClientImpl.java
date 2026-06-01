package com.aerovhyn.routing.service.impl;

import com.aerovhyn.common.util.HaversineUtils;
import com.aerovhyn.routing.service.OsrmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OsrmClientImpl implements OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClientImpl.class);
    private final WebClient webClient;

    public OsrmClientImpl(@Value("${aerovhyn.osrm.base-url:http://router.project-osrm.org}") String osrmBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(osrmBaseUrl).build();
    }

    @Override
    public double getEtaMinutes(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            double distanceKm = getDistanceKm(fromLat, fromLon, toLat, toLon);
            return Math.round((distanceKm / 50.0) * 60 * 10) / 10.0;
        } catch (Exception e) {
            log.warn("OSRM ETA failed, using Haversine fallback: {}", e.getMessage());
            double distanceKm = HaversineUtils.distanceKm(fromLat, fromLon, toLat, toLon);
            return Math.round((distanceKm / 50.0) * 60 * 10) / 10.0;
        }
    }

    @Override
    public double getDistanceKm(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            String coords = fromLon + "," + fromLat + ";" + toLon + "," + toLat;
            String url = "/route/v1/driving/" + coords + "?overview=false";

            var response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .block(java.time.Duration.ofSeconds(4));

            if (response != null && "Ok".equals(response.get("code"))) {
                java.util.List<java.util.Map<String, Object>> routes =
                        (java.util.List<java.util.Map<String, Object>>) response.get("routes");
                if (routes != null && !routes.isEmpty()) {
                    double distanceMeters = ((Number) routes.get(0).get("distance")).doubleValue();
                    return distanceMeters / 1000.0;
                }
            }
        } catch (Exception e) {
            log.warn("OSRM distance failed, using Haversine fallback: {}", e.getMessage());
        }
        return HaversineUtils.distanceKm(fromLat, fromLon, toLat, toLon);
    }

    @Override
    public Map<String, RouteInfo> getBatchRoutes(double fromLat, double fromLon, List<HospitalLocation> targets) {
        Map<String, RouteInfo> results = new HashMap<>();
        if (targets == null || targets.isEmpty()) {
            return results;
        }

        try {
            StringBuilder coordsBuilder = new StringBuilder();
            coordsBuilder.append(fromLon).append(",").append(fromLat);
            for (HospitalLocation target : targets) {
                coordsBuilder.append(";").append(target.lon()).append(",").append(target.lat());
            }

            String url = "/table/v1/driving/" + coordsBuilder.toString() + "?sources=0&annotations=distance,duration";

            var response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .block(java.time.Duration.ofSeconds(4));

            if (response != null && "Ok".equals(response.get("code"))) {
                List<List<Number>> distances = (List<List<Number>>) response.get("distances");
                List<List<Number>> durations = (List<List<Number>>) response.get("durations");

                if (distances != null && !distances.isEmpty() && durations != null && !durations.isEmpty()) {
                    List<Number> sourceDistances = distances.get(0);
                    List<Number> sourceDurations = durations.get(0);

                    for (int i = 0; i < targets.size(); i++) {
                        HospitalLocation target = targets.get(i);
                        if (i + 1 < sourceDistances.size() && i + 1 < sourceDurations.size()) {
                            Number distObj = sourceDistances.get(i + 1);
                            Number durObj = sourceDurations.get(i + 1);

                            if (distObj != null && durObj != null) {
                                double distanceKm = distObj.doubleValue() / 1000.0;
                                double durationMin = durObj.doubleValue() / 60.0;
                                results.put(target.id(), new RouteInfo(distanceKm, durationMin));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OSRM batch table routing failed: {}", e.getMessage());
        }

        // Apply clean straight-line fallback for any missing targets
        for (HospitalLocation target : targets) {
            if (!results.containsKey(target.id())) {
                double distanceKm = HaversineUtils.distanceKm(fromLat, fromLon, target.lat(), target.lon());
                double durationMin = Math.round((distanceKm / 50.0) * 60 * 10) / 10.0;
                results.put(target.id(), new RouteInfo(distanceKm, durationMin));
            }
        }

        return results;
    }
}

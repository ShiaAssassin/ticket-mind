package com.ticketmind.mcpserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class StationCodeService {

    private static final String STATION_NAME_MAP_PATH = "station-data/station_name_map.json";

    private final Map<String, String> stationNameToCode;
    private final Map<String, String> codeToStationName;

    public StationCodeService(ObjectMapper objectMapper) throws IOException {
        stationNameToCode = objectMapper.readValue(
                new ClassPathResource(STATION_NAME_MAP_PATH).getInputStream(),
                new TypeReference<LinkedHashMap<String, String>>() {
                });
        codeToStationName = new LinkedHashMap<>();
        stationNameToCode.forEach((stationName, stationCode) ->
                codeToStationName.put(stationCode.toUpperCase(Locale.ROOT), stationName));
    }

    public Optional<String> resolveStationCode(String station) {
        if (!StringUtils.hasText(station)) {
            return Optional.empty();
        }

        String normalized = station.trim();
        String code = stationNameToCode.get(normalized);
        if (StringUtils.hasText(code)) {
            return Optional.of(code);
        }

        String upperCase = normalized.toUpperCase(Locale.ROOT);
        if (codeToStationName.containsKey(upperCase)) {
            return Optional.of(upperCase);
        }

        return Optional.empty();
    }

    public String stationNameOrCode(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return "";
        }
        return codeToStationName.getOrDefault(stationCode.toUpperCase(Locale.ROOT), stationCode);
    }
}

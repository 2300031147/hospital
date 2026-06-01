package com.aerovhyn.routing.service;

import com.aerovhyn.common.dto.SystemSettingsDto;

public interface SystemSettingsService {
    SystemSettingsDto getSettings();
    SystemSettingsDto updateSettings(SystemSettingsDto settings);
}

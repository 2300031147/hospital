package com.aerovhyn.core.service;

import com.aerovhyn.common.dto.HospitalCreateDto;
import com.aerovhyn.common.dto.HospitalInfoDto;
import com.aerovhyn.common.dto.HospitalUpdateDto;

import java.util.List;

public interface HospitalService {
    List<HospitalInfoDto> getAll(String status);
    HospitalInfoDto getById(Long id);
    HospitalInfoDto create(HospitalCreateDto dto);
    HospitalInfoDto update(Long id, HospitalUpdateDto dto);
    void delete(Long id);
}

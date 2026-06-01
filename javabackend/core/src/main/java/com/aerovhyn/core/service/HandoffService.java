package com.aerovhyn.core.service;

public interface HandoffService {
    void acknowledge(Long hospitalId);
    void accept(Long hospitalId, Long ambulanceId);
    void releaseBed(Long hospitalId);
    void discharge(Long hospitalId);
    void completeDispatch(Long ambulanceId);
}

package ckaanf.controlplane.streaming.live.domain.service;

public interface ProcessChecker {
    boolean isAlive(Long pid);
}

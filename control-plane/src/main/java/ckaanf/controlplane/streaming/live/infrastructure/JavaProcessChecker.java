package ckaanf.controlplane.streaming.live.infrastructure;

import ckaanf.controlplane.streaming.live.domain.service.ProcessChecker;

public class JavaProcessChecker implements ProcessChecker {
    @Override
    public boolean isAlive(Long pid) {
        if (pid == null) return false;
        return ProcessHandle.of(pid)
                .map(ProcessHandle::isAlive)
                .orElse(false);
    }
}

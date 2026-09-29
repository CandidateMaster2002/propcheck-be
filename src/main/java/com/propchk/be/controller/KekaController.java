package com.propchk.be.controller;

import com.propchk.be.service.ConflictDetectionService;
import com.propchk.be.service.KekaEngineerSyncService;
import com.propchk.be.service.KekaLeaveSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/keka")
public class KekaController {

    private final KekaEngineerSyncService engineerSyncService;
    private final KekaLeaveSyncService leaveSyncService;
    private final ConflictDetectionService conflictDetectionService;

    public KekaController(KekaEngineerSyncService engineerSyncService,
                          KekaLeaveSyncService leaveSyncService,
                          ConflictDetectionService conflictDetectionService) {
        this.engineerSyncService = engineerSyncService;
        this.leaveSyncService = leaveSyncService;
        this.conflictDetectionService = conflictDetectionService;
    }

    /** Manually trigger full Keka sync (engineers + leaves + conflict detection) */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> syncAll() {
        int engineers = engineerSyncService.syncEngineers();
        int leaves = leaveSyncService.syncLeaves();
        int conflicts = conflictDetectionService.detectAndMarkConflicts();
        return ResponseEntity.ok(Map.of(
                "engineersSynced", engineers,
                "leavesSynced", leaves,
                "conflictsDetected", conflicts
        ));
    }
}

package com.propchk.be.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KekaScheduledSync {

    private static final Logger logger = LoggerFactory.getLogger(KekaScheduledSync.class);

    private final KekaEngineerSyncService engineerSyncService;
    private final KekaLeaveSyncService leaveSyncService;
    private final ConflictDetectionService conflictDetectionService;

    public KekaScheduledSync(KekaEngineerSyncService engineerSyncService,
                             KekaLeaveSyncService leaveSyncService,
                             ConflictDetectionService conflictDetectionService) {
        this.engineerSyncService = engineerSyncService;
        this.leaveSyncService = leaveSyncService;
        this.conflictDetectionService = conflictDetectionService;
    }

    /**
     * Runs every night at midnight IST.
     * 1. Syncs engineers from Keka
     * 2. Syncs leaves for next 90 days
     * 3. Detects booking conflicts
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void runNightlySync() {
        logger.info("=== Nightly Keka Sync Started ===");
        try {
            int engineers = engineerSyncService.syncEngineers();
            logger.info("Engineers synced: {}", engineers);

            int leaves = leaveSyncService.syncLeaves();
            logger.info("Leaves synced: {}", leaves);

            int conflicts = conflictDetectionService.detectAndMarkConflicts();
            logger.info("Conflicts detected: {}", conflicts);

        } catch (Exception e) {
            logger.error("Nightly Keka sync failed: {}", e.getMessage(), e);
        }
        logger.info("=== Nightly Keka Sync Complete ===");
    }
}

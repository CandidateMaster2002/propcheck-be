package com.propchk.be.dto;

import lombok.Data;

@Data
public class RescheduleBookingRequest {
    /** New date in yyyy-MM-dd format */
    private String date;
    /** MORNING or EVENING */
    private String slot;
    /** e.g. "10:00", "10:30", "14:00" */
    private String slotTime;
    /** User making the reschedule request */
    private Long requestedByUserId;
}

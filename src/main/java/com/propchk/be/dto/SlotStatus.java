package com.propchk.be.dto;

import lombok.Data;

@Data
public class SlotStatus {
    /** AVAILABLE, LEAVE, BOOKED, or CONFLICT */
    private String status;
    
    /** Leave type name, or Customer Name if booked */
    private String label;
    
    /** Only populated if status is BOOKED or CONFLICT */
    private Long bookingId;
    
    public SlotStatus(String status, String label, Long bookingId) {
        this.status = status;
        this.label = label;
        this.bookingId = bookingId;
    }
}

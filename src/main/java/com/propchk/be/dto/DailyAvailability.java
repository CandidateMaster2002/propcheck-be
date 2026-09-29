package com.propchk.be.dto;

import lombok.Data;

@Data
public class DailyAvailability {
    private String date; // yyyy-MM-dd
    private boolean morningAvailable;
    private boolean eveningAvailable;
    private boolean anyAvailable;
}

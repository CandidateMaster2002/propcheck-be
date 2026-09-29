package com.propchk.be.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.Map;

@Data
public class EngineerSchedule {
    private String employeeNumber;
    private String name;
    private String city;
    private String jobTitle;
    
    /** 
     * Map of Date -> Map of Slot ("MORNING" / "EVENING") -> SlotStatus
     */
    private Map<String, Map<String, SlotStatus>> schedule;
}

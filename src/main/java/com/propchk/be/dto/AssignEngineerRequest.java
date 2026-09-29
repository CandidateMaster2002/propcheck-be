package com.propchk.be.dto;

import lombok.Data;

@Data
public class AssignEngineerRequest {
    private String engineerEmployeeNumber;
    private Long cityHeadUserId;
}

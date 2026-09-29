package com.propchk.be.repository;

import com.propchk.be.entity.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EngineerRepository extends JpaRepository<Engineer, Long> {

    Optional<Engineer> findByEmployeeNumber(String employeeNumber);

    /** All active QC engineers in a mapped city */
    List<Engineer> findByMappedCityAndEmploymentStatusAndExitStatus(
            String mappedCity, int employmentStatus, int exitStatus);

    /** All active QC engineers across all cities */
    List<Engineer> findByEmploymentStatusAndExitStatus(int employmentStatus, int exitStatus);
}

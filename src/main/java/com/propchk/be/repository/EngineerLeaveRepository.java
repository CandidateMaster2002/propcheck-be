package com.propchk.be.repository;

import com.propchk.be.entity.EngineerLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EngineerLeaveRepository extends JpaRepository<EngineerLeave, Long> {

    /** All approved/pending leaves for a specific engineer that overlap a given date */
    @Query("SELECT l FROM EngineerLeave l WHERE l.employeeNumber = :empNum " +
           "AND l.status = 'Approved' " +
           "AND l.fromDate <= :date AND l.toDate >= :date")
    List<EngineerLeave> findApprovedLeavesOnDate(
            @Param("empNum") String employeeNumber,
            @Param("date") LocalDate date);

    /** Delete all leaves for re-sync */
    void deleteByEmployeeNumber(String employeeNumber);

    /** Find CONFIRMED bookings whose engineer has an approved leave on booking date */
    @Query("SELECT l FROM EngineerLeave l WHERE l.status = 'Approved' " +
           "AND l.fromDate <= :date AND l.toDate >= :date")
    List<EngineerLeave> findAllApprovedLeavesOnDate(@Param("date") LocalDate date);

    /** All leaves for a set of employee numbers in a date range */
    @Query("SELECT l FROM EngineerLeave l WHERE l.employeeNumber IN :empNums " +
           "AND l.status = 'Approved' " +
           "AND l.toDate >= :fromDate AND l.fromDate <= :toDate")
    List<EngineerLeave> findApprovedLeavesInRange(
            @Param("empNums") List<String> employeeNumbers,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}

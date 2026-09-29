package com.propchk.be.repository;

import com.propchk.be.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByZohoLeadId(String zohoLeadId);
    
    Optional<Lead> findByEmail(String email);

    List<Lead> findByCity(String city);

    Optional<Lead> findTopByZohoModifiedTimeIsNotNullOrderByZohoModifiedTimeDesc();

    @org.springframework.data.jpa.repository.Query("SELECT l FROM Lead l WHERE " +
       "(:city = '' OR (:city != 'Others' AND l.city = :city) OR (:city = 'Others' AND l.city NOT IN ('Hyderabad', 'Bangalore', 'Pune', 'Mumbai', 'Delhi NCR', 'Chennai'))) AND " +
       "(:owner = '' OR l.leadOwner = :owner) AND " +
       "(:search = '' OR LOWER(l.customerName) LIKE LOWER(CONCAT('%', :search, '%')) " +
       "  OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
       "  OR l.phone LIKE CONCAT('%', :search, '%')) AND " +
       "(:bFilter = 'ALL' OR " +
       "  (:bFilter = 'UNBOOKED' AND NOT EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id)) OR " +
       "  (:bFilter = 'PENDING_ENGINEER' AND EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id AND b.status IN ('PENDING_ENGINEER_ASSIGNMENT', 'PENDING_APPROVAL'))) OR " +
       "  (:bFilter = 'ENGINEER_ALLOTTED' AND EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id AND b.status IN ('CONFIRMED', 'CONFLICT'))) OR " +
       "  (:bFilter = 'INSPECTION_DONE' AND (LOWER(l.reportStatus) LIKE '%done%' OR EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id AND b.status IN ('INSPECTION_DONE', 'COMPLETED')))) OR " +
       "  (:bFilter = 'VALIDATION_DONE' AND EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id AND b.status = 'VALIDATION_DONE')) OR " +
       "  (:bFilter = 'REPORT_SENT' AND LOWER(l.reportStatus) LIKE '%sent%') OR " +
       "  (:bFilter = 'POSTPONED_CANCELLED' AND EXISTS (SELECT b FROM Booking b WHERE b.leadId = l.id AND b.status IN ('CANCELLED', 'POSTPONED')) AND NOT EXISTS (SELECT b2 FROM Booking b2 WHERE b2.leadId = l.id AND b2.status IN ('PENDING_ENGINEER_ASSIGNMENT', 'PENDING_APPROVAL', 'CONFIRMED', 'CONFLICT', 'INSPECTION_DONE', 'COMPLETED', 'VALIDATION_DONE'))) " +
       ") ORDER BY l.id DESC")
    List<Lead> searchLeads(@org.springframework.data.repository.query.Param("city") String city,
                           @org.springframework.data.repository.query.Param("search") String search,
                           @org.springframework.data.repository.query.Param("bFilter") String bookingFilter,
                           @org.springframework.data.repository.query.Param("owner") String owner);
}

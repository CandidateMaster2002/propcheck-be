package com.propchk.be.repository;

import com.propchk.be.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByLeadId(Long leadId);

    List<Booking> findByStatus(String status);

    List<Booking> findByCity(String city);

    List<Booking> findByCityAndStatus(String city, String status);

    /**
     * Count bookings that actually consume a slot.
     * PENDING_APPROVAL is deliberately EXCLUDED — per business rule,
     * a pending booking does NOT lock the slot (proposal Section 4.2).
     * CONFLICT included because engineer is still technically assigned.
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.city = :city AND b.date = :date " +
           "AND b.slot = :slot " +
           "AND b.status IN ('PENDING_ENGINEER_ASSIGNMENT', 'CONFIRMED', 'CONFLICT')")
    long countActiveBookings(
            @Param("city") String city,
            @Param("date") LocalDate date,
            @Param("slot") String slot);

    /** Check if a lead already has a booking of a given type (INSPECTION or REINSPECTION) */
    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.leadId = :leadId " +
           "AND b.bookingType = :type " +
           "AND b.status NOT IN ('CANCELLED')")
    boolean existsActiveBookingForLeadAndType(
            @Param("leadId") Long leadId,
            @Param("type") String bookingType);

    /** Fetch active bookings for a list of leads (to populate frontend dashboard status) */
    @Query("SELECT b FROM Booking b WHERE b.leadId IN :leadIds AND b.status NOT IN ('CANCELLED', 'POSTPONED')")
    List<Booking> findActiveBookingsByLeadIds(@Param("leadIds") List<Long> leadIds);

    /** All future CONFIRMED bookings for a specific engineer */
    @Query("SELECT b FROM Booking b WHERE b.engineerEmployeeNumber = :empNum " +
           "AND b.status = 'CONFIRMED' AND b.date >= :fromDate")
    List<Booking> findConfirmedFutureBookingsForEngineer(
            @Param("empNum") String employeeNumber,
            @Param("fromDate") LocalDate fromDate);

    /** Fetch active bookings for a city within a date range (for bulk availability calendar check) */
    @Query("SELECT b FROM Booking b WHERE b.city = :city " +
           "AND b.date >= :startDate AND b.date <= :endDate " +
           "AND b.status IN ('PENDING_ENGINEER_ASSIGNMENT', 'CONFIRMED', 'CONFLICT')")
    List<Booking> findActiveBookingsByCityAndDateRange(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Fetch bookings for multiple engineers within a date range (for schedule matrix) */
    @Query("SELECT b FROM Booking b WHERE b.engineerEmployeeNumber IN :empNums " +
           "AND b.date >= :startDate AND b.date <= :endDate " +
           "AND b.status IN ('CONFIRMED', 'CONFLICT')")
    List<Booking> findBookingsForEngineersInRange(
            @Param("empNums") List<String> employeeNumbers,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** All CONFLICT bookings for a city */
    List<Booking> findByCityAndStatusOrderByDateAsc(String city, String status);

    /** All bookings a user created */
    List<Booking> findByCreatedByUserIdOrderByDateDesc(Long userId);

    // =========== ANALYTICS QUERIES ===========

    /** Count bookings grouped by status */
    @Query("SELECT b.status, COUNT(b) FROM Booking b GROUP BY b.status")
    List<Object[]> countByStatus();

    /** Count bookings grouped by city */
    @Query("SELECT b.city, COUNT(b) FROM Booking b GROUP BY b.city")
    List<Object[]> countByCity();

    /** Count bookings grouped by booking type */
    @Query("SELECT b.bookingType, COUNT(b) FROM Booking b GROUP BY b.bookingType")
    List<Object[]> countByBookingType();

    /** All bookings for a specific date (for daily calendar view) */
    List<Booking> findByDateOrderByCityAscSlotAsc(LocalDate date);

    /** Bookings in a date range */
    @Query("SELECT b FROM Booking b WHERE b.date >= :from AND b.date <= :to ORDER BY b.date ASC")
    List<Booking> findInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Count upcoming non-cancelled bookings */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.date >= :today AND b.status NOT IN ('CANCELLED')")
    long countUpcomingBookings(@Param("today") LocalDate today);

    /** Count per city per status (for city-wise breakdown table) */
    @Query("SELECT b.city, b.status, COUNT(b) FROM Booking b GROUP BY b.city, b.status")
    List<Object[]> countByCityAndStatus();
}

package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository para InstrumentEntity.
 */
@Repository
public interface InstrumentJpaRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByTicker(String ticker);

    @Query("SELECT i FROM InstrumentEntity i WHERE LOWER(i.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<InstrumentEntity> findByTickerOrNameContainingIgnoreCase(@Param("query") String query, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(i) FROM InstrumentEntity i WHERE LOWER(i.ticker) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    long countByTickerOrNameContainingIgnoreCase(@Param("query") String query);

    List<InstrumentEntity> findByType(ar.com.cocos.challenge.domain.model.InstrumentType type);
}
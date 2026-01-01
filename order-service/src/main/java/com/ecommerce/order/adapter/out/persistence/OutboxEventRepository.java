package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for outbox_event table.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Find all unprocessed events ordered by creation time.
     */
    List<OutboxEventEntity> findByProcessedFalseOrderByCreatedAtAsc();
}

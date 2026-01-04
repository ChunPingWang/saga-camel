package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;
import com.ecommerce.order.application.port.out.TransactionLogPort;
import com.ecommerce.order.domain.model.TransactionLog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistence adapter implementing TransactionLogPort.
 * Translates between domain model and JPA entities.
 */
@Component
public class TransactionLogPersistenceAdapter implements TransactionLogPort {

    private final TransactionLogRepository repository;

    public TransactionLogPersistenceAdapter(TransactionLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionLog save(TransactionLog transactionLog) {
        TransactionLogEntity entity = new TransactionLogEntity(
                transactionLog.getTxId().toString(),
                transactionLog.getOrderId().toString(),
                transactionLog.getServiceName().name(),
                transactionLog.getStatus().getCode()
        );
        entity.setErrorMessage(transactionLog.getErrorMessage());
        entity.setRetryCount(transactionLog.getRetryCount());

        TransactionLogEntity saved = repository.save(entity);
        transactionLog.setId(saved.getId());
        return transactionLog;
    }

    @Override
    public List<TransactionLog> findLatestByTxId(String txId) {
        Map<String, TransactionLogEntity> latestByService = new HashMap<>();

        List<TransactionLogEntity> logs = repository.findByTxIdOrderByCreatedAtAsc(txId);
        for (TransactionLogEntity log : logs) {
            latestByService.put(log.getServiceName(), log);
        }

        return latestByService.values().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public TransactionLog recordStatus(UUID txId, UUID orderId, ServiceName serviceName, TransactionStatus status) {
        return recordStatusWithError(txId, orderId, serviceName, status, null);
    }

    @Override
    public TransactionLog recordStatusWithError(UUID txId, UUID orderId, ServiceName serviceName,
                                                  TransactionStatus status, String errorMessage) {
        return recordStatusWithRetry(txId, orderId, serviceName, status, errorMessage, 0);
    }

    @Override
    public TransactionLog recordStatusWithRetry(UUID txId, UUID orderId, ServiceName serviceName,
                                                 TransactionStatus status, String errorMessage, int retryCount) {
        TransactionLogEntity entity = new TransactionLogEntity(
                txId.toString(),
                orderId.toString(),
                serviceName.name(),
                status.getCode()
        );
        entity.setErrorMessage(errorMessage);
        entity.setRetryCount(retryCount);

        TransactionLogEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<TransactionLog> findByTxId(UUID txId) {
        return repository.findByTxIdOrderByCreatedAtAsc(txId.toString())
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceName> findSuccessfulServices(UUID txId) {
        return repository.findSuccessfulServices(txId.toString())
                .stream()
                .map(ServiceName::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public Map<ServiceName, TransactionStatus> getLatestStatuses(UUID txId) {
        Map<ServiceName, TransactionStatus> result = new HashMap<>();

        List<TransactionLogEntity> logs = repository.findByTxIdOrderByCreatedAtAsc(txId.toString());

        // Group by service and get the latest status for each
        logs.stream()
                .collect(Collectors.groupingBy(TransactionLogEntity::getServiceName))
                .forEach((serviceName, entities) -> {
                    TransactionLogEntity latest = entities.stream()
                            .max(Comparator.comparing(TransactionLogEntity::getCreatedAt))
                            .orElseThrow();
                    result.put(
                            ServiceName.valueOf(serviceName),
                            TransactionStatus.valueOf(latest.getStatus())
                    );
                });

        return result;
    }

    @Override
    public Optional<TransactionLog> getLatestForService(UUID txId, ServiceName serviceName) {
        List<TransactionLogEntity> entities = repository
                .findByTxIdAndServiceNameOrderByCreatedAtDesc(txId.toString(), serviceName.name());

        return entities.isEmpty() ? Optional.empty() : Optional.of(toDomain(entities.get(0)));
    }

    @Override
    public List<UUID> findTimedOutTransactions(LocalDateTime olderThan) {
        return repository.findTimedOutTransactions(olderThan)
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    @Override
    public List<UnfinishedTransaction> findUnfinishedTransactions() {
        return repository.findUnfinishedTransactionsWithOrderId()
                .stream()
                .map(row -> new UnfinishedTransaction(
                        UUID.fromString((String) row[0]),
                        UUID.fromString((String) row[1])
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void recordNotifiedAt(UUID txId, ServiceName serviceName, LocalDateTime notifiedAt) {
        List<TransactionLogEntity> entities = repository
                .findByTxIdAndServiceNameOrderByCreatedAtDesc(txId.toString(), serviceName.name());

        if (!entities.isEmpty()) {
            TransactionLogEntity latest = entities.get(0);
            latest.setNotifiedAt(notifiedAt);
            repository.save(latest);
        }
    }

    @Override
    public boolean isTransactionComplete(UUID txId, List<ServiceName> expectedServices) {
        List<ServiceName> successfulServices = findSuccessfulServices(txId);
        return successfulServices.containsAll(expectedServices);
    }

    @Override
    public List<TransactionLog> findByOrderId(UUID orderId) {
        return repository.findByOrderIdOrderByCreatedAtDesc(orderId.toString())
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UUID> findDistinctTxIdsByOrderId(UUID orderId) {
        return repository.findDistinctTxIdsByOrderId(orderId.toString())
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private TransactionLog toDomain(TransactionLogEntity entity) {
        TransactionLog log = TransactionLog.createWithRetry(
                UUID.fromString(entity.getTxId()),
                UUID.fromString(entity.getOrderId()),
                ServiceName.valueOf(entity.getServiceName()),
                TransactionStatus.valueOf(entity.getStatus()),
                entity.getErrorMessage(),
                entity.getRetryCount() != null ? entity.getRetryCount() : 0
        );
        log.setId(entity.getId());
        if (entity.getNotifiedAt() != null) {
            log.setNotifiedAt(entity.getNotifiedAt());
        }
        return log;
    }
}

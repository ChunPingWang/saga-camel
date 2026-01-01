package com.ecommerce.order.application.port.out;

import com.ecommerce.common.domain.ServiceName;
import com.ecommerce.common.domain.TransactionStatus;

import java.util.UUID;

/**
 * Output port for WebSocket notifications to clients.
 */
public interface WebSocketPort {

    /**
     * Send a notification message to connected clients for a specific transaction.
     */
    void sendNotification(String txId, Object message);

    /**
     * Send a status update to connected clients for a specific transaction.
     */
    void sendStatusUpdate(UUID txId, UUID orderId, TransactionStatus status,
                          ServiceName currentStep, String message);

    /**
     * Send a processing notification.
     */
    default void sendProcessing(UUID txId, UUID orderId, ServiceName service) {
        sendStatusUpdate(txId, orderId, TransactionStatus.U, service,
                "正在處理: " + service.name());
    }

    /**
     * Send a success notification.
     */
    default void sendSuccess(UUID txId, UUID orderId, ServiceName service) {
        sendStatusUpdate(txId, orderId, TransactionStatus.S, service,
                service.name() + " 處理成功");
    }

    /**
     * Send a failure notification.
     */
    default void sendFailure(UUID txId, UUID orderId, ServiceName service, String error) {
        sendStatusUpdate(txId, orderId, TransactionStatus.F, service,
                "服務呼叫失敗: " + error);
    }

    /**
     * Send a rollback progress notification.
     */
    default void sendRollbackProgress(UUID txId, UUID orderId, ServiceName service) {
        sendStatusUpdate(txId, orderId, TransactionStatus.R, service,
                service.name() + " 回滾成功");
    }

    /**
     * Send a transaction completed notification.
     */
    default void sendCompleted(UUID txId, UUID orderId) {
        sendStatusUpdate(txId, orderId, TransactionStatus.S, null,
                "訂單交易完成");
    }

    /**
     * Send a transaction rolled back notification.
     */
    default void sendRolledBack(UUID txId, UUID orderId) {
        sendStatusUpdate(txId, orderId, TransactionStatus.D, null,
                "交易已回滾完成");
    }

    /**
     * Send a rollback failed notification.
     */
    default void sendRollbackFailed(UUID txId, UUID orderId, String error) {
        sendStatusUpdate(txId, orderId, TransactionStatus.RF, null,
                "回滾失敗，需人工介入: " + error);
    }
}

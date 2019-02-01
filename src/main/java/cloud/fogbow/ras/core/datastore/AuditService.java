package cloud.fogbow.ras.core.datastore;

import cloud.fogbow.ras.core.datastore.orderstorage.OrderStateChangeRepository;
import cloud.fogbow.ras.core.models.auditing.AuditableSyncRequest;
import cloud.fogbow.ras.core.datastore.orderstorage.AuditableOrderStateChange;
import cloud.fogbow.ras.core.datastore.orderstorage.SyncRequestRepository;
import cloud.fogbow.ras.core.models.orders.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class AuditService {

    @Autowired
    private OrderStateChangeRepository orderTimestampRepository;

    @Autowired
    private SyncRequestRepository syncRequestRepository;

    public void registerStateChange(Order order) {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        AuditableOrderStateChange auditableOrderStateChange = new AuditableOrderStateChange(currentTimestamp, order, order.getOrderState());
        this.orderTimestampRepository.save(auditableOrderStateChange);
    }

    public void registerSyncRequest(AuditableSyncRequest request) {
        this.syncRequestRepository.save(request);
    }
}

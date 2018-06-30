package org.fogbowcloud.manager.core.datastore;

import java.util.Collection;
import java.util.List;
import org.fogbowcloud.manager.core.models.orders.NetworkOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.orders.VolumeOrder;
import org.fogbowcloud.manager.core.models.tokens.Token;

public interface ManagerDatastore {

    public Order addOrder(Order order);

    public Iterable<Order> getAllOrders();

    public Order getOrdersByUserId(Long id);

    public Order getOrdersByToken(Token token);

    public List<Order> getOrderByState(OrderState orderState);

    public void updateOrder(Order order);

    public void deleteOrder(Order order);

    public List<Order> findAllComputeOrder();

    public Collection<NetworkOrder> findAllNetworkOrders();

    public Collection<VolumeOrder> findAllVolumeOrders();
}

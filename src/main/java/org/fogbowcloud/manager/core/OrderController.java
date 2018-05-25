package org.fogbowcloud.manager.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.fogbowcloud.manager.api.local.http.ComputeOrdersController;
import org.fogbowcloud.manager.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.exceptions.OrderStateTransitionException;
import org.fogbowcloud.manager.core.exceptions.PropertyNotSpecifiedException;
import org.fogbowcloud.manager.core.exceptions.RequestException;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.instanceprovider.LocalInstanceProvider;
import org.fogbowcloud.manager.core.instanceprovider.RemoteInstanceProvider;
import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.linkedlist.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.orders.OrderType;
import org.fogbowcloud.manager.core.models.orders.instances.Instance;
import org.fogbowcloud.manager.core.models.token.FederationUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeOrdersController.class);
    private final InstanceProvider localInstanceProvider;
    private final InstanceProvider remoteInstanceProvider;
    private final String localMemberId;
    private SharedOrderHolders orderHolders;

    public OrderController(
        Properties properties,
        LocalInstanceProvider localInstanceProvider,
        RemoteInstanceProvider remoteInstanceProvider) {
        this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_ID_KEY);
        this.localInstanceProvider = localInstanceProvider;
        this.remoteInstanceProvider = remoteInstanceProvider;
        this.orderHolders = SharedOrderHolders.getInstance();
    }

    public List<Order> getAllOrders(FederationUser federationUser, OrderType type)
        throws UnauthorizedException {
        Collection<Order> orders = this.orderHolders.getActiveOrdersMap().values();

        List<Order> requestedOrders =
            orders.stream()
                .filter(order -> order.getType().equals(type))
                .filter(order -> order.getFederationUser().equals(federationUser))
                .collect(Collectors.toList());

        return requestedOrders;
    }

    public Order getOrder(String id, FederationUser federationUser, OrderType orderType) {
        Order requestedOrder = this.orderHolders.getActiveOrdersMap().get(id);

        // TODO Do we need to perform this check?
        // We might want to move this to the AuthorizationPlugin
        FederationUser orderOwner = requestedOrder.getFederationUser();
        if (!orderOwner.equals(federationUser)) {
            return null;
        }

        if (!requestedOrder.getType().equals(orderType)) {
            return null;
        }

        return requestedOrder;
    }

    public void deleteOrder(Order order) throws OrderManagementException {
        synchronized (order) {
            if (order == null) {
                String message = "Cannot delete a null order";
                throw new OrderManagementException(message);
            }

            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.CLOSED)) {
                try {
                    OrderStateTransitioner.transition(order, OrderState.CLOSED);
                } catch (OrderStateTransitionException e) {
                    LOGGER.error(
                        "This should never happen. Error trying to change the status from"
                            + order.getOrderState()
                            + " to closed for order ["
                            + order.getId()
                            + "]",
                        e);
                }
            } else {
                String message = "Order [" + order.getId() + "] is already in the closed state";
                throw new OrderManagementException(message);
            }
        }
    }

    public void activateOrder(Order order, FederationUser federationUser)
        throws OrderManagementException {
        if (order == null) {
            String message = "Can't process new order request. Order reference is null.";
            throw new OrderManagementException(message);
        }

        order.setFederationUser(federationUser);
        addOrderInActiveOrdersMap(order);
    }

    private void addOrderInActiveOrdersMap(Order order) throws OrderManagementException {
        String orderId = order.getId();
        Map<String, Order> activeOrdersMap = this.orderHolders.getActiveOrdersMap();
        SynchronizedDoublyLinkedList openOrdersList = this.orderHolders.getOpenOrdersList();

        synchronized (order) {
            if (activeOrdersMap.containsKey(orderId)) {
                String message =
                    String.format("Order with id %s is already in active orders map.", orderId);
                throw new OrderManagementException(message);
            }

            order.setOrderState(OrderState.OPEN);
            activeOrdersMap.put(orderId, order);
            openOrdersList.addItem(order);
        }
    }

    public void removeOrderFromActiveOrdersMap(Order order) {
        Map<String, Order> activeOrdersMap = this.orderHolders.getActiveOrdersMap();
        synchronized (order) {
            if (activeOrdersMap.containsKey(order.getId())) {
                activeOrdersMap.remove(order.getId());
            } else {
                String message =
                    "Tried to remove order %s from the active orders but it was not active";
                LOGGER.error(String.format(message, order.getId()));
            }
        }
    }

    private InstanceProvider getInstanceProviderForOrder(Order order) {
        InstanceProvider instanceProvider;

        synchronized (order) {
            if (order.isLocal(this.localMemberId)) {
                LOGGER.debug("The order [" + order.getId() + "] is local");

                instanceProvider = this.localInstanceProvider;
            } else {
                LOGGER.debug(
                    "The open order ["
                        + order.getId()
                        + "] is remote for the "
                        + "member ["
                        + order.getProvidingMember()
                        + "]");

                instanceProvider = this.remoteInstanceProvider;
            }
        }

        return instanceProvider;
    }

    public Instance getResourceInstance(Order order)
        throws PropertyNotSpecifiedException, TokenCreationException, RequestException, UnauthorizedException, InstanceNotFoundException {
        synchronized (order) {
            InstanceProvider instanceProvider = getInstanceProviderForOrder(order);
            return instanceProvider.getInstance(order);
        }
    }
}

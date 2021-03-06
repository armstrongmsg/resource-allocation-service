package cloud.fogbow.ras.core;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.InternalServerErrorException;
import cloud.fogbow.common.models.linkedlists.SynchronizedDoublyLinkedList;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SharedOrderHolders {
    private static final Logger LOGGER = Logger.getLogger(SharedOrderHolders.class);

    private static SharedOrderHolders instance;

    private Map<String, Order> activeOrdersMap;
    private SynchronizedDoublyLinkedList<Order> openOrders;
    private SynchronizedDoublyLinkedList<Order> selectedOrders;
    private SynchronizedDoublyLinkedList<Order> spawningOrders;
    private SynchronizedDoublyLinkedList<Order> failedAfterSuccessfulRequestOrders;
    private SynchronizedDoublyLinkedList<Order> failedOnRequestOrders;
    private SynchronizedDoublyLinkedList<Order> fulfilledOrders;
    private SynchronizedDoublyLinkedList<Order> unableToCheckStatus;
    private SynchronizedDoublyLinkedList<Order> remoteProviderOrders;
    private SynchronizedDoublyLinkedList<Order> assignedForDeletionOrders;
    private SynchronizedDoublyLinkedList<Order> checkingDeletionOrders;

    public SharedOrderHolders() {
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        this.activeOrdersMap = new ConcurrentHashMap<>();

        try {
            // All orders in the PENDING state have remote providers
            this.remoteProviderOrders = databaseManager.readActiveOrders(OrderState.PENDING);
            this.openOrders = databaseManager.readActiveOrders(OrderState.OPEN);
            // An order in the OPEN state should be kept in the openOrders list even if its provider is remote
            // because the order has not yet been sent to the remote provider, and will be dealt with by the
            // OpenProcessor.
            addOrdersToMap(this.openOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.OPEN, this.activeOrdersMap.size()));
            this.selectedOrders = databaseManager.readActiveOrders(OrderState.SELECTED);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.selectedOrders);
            addOrdersToMap(this.selectedOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.SELECTED, this.activeOrdersMap.size()));
            this.spawningOrders = databaseManager.readActiveOrders(OrderState.SPAWNING);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.spawningOrders);
            addOrdersToMap(this.spawningOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.SPAWNING, this.activeOrdersMap.size()));
            this.failedAfterSuccessfulRequestOrders = databaseManager.readActiveOrders(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.failedAfterSuccessfulRequestOrders);
            addOrdersToMap(this.failedAfterSuccessfulRequestOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST, this.activeOrdersMap.size()));
            this.failedOnRequestOrders = databaseManager.readActiveOrders(OrderState.FAILED_ON_REQUEST);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.failedOnRequestOrders);
            addOrdersToMap(this.failedOnRequestOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.FAILED_ON_REQUEST, this.activeOrdersMap.size()));
            this.fulfilledOrders = databaseManager.readActiveOrders(OrderState.FULFILLED);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.fulfilledOrders);
            addOrdersToMap(this.fulfilledOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.FULFILLED, this.activeOrdersMap.size()));
            this.unableToCheckStatus = databaseManager.readActiveOrders(OrderState.UNABLE_TO_CHECK_STATUS);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.unableToCheckStatus);
            addOrdersToMap(this.unableToCheckStatus, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.UNABLE_TO_CHECK_STATUS, this.activeOrdersMap.size()));
            this.assignedForDeletionOrders = databaseManager.readActiveOrders(OrderState.ASSIGNED_FOR_DELETION);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.assignedForDeletionOrders);
            addOrdersToMap(this.assignedForDeletionOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.ASSIGNED_FOR_DELETION, this.activeOrdersMap.size()));
            this.checkingDeletionOrders = databaseManager.readActiveOrders(OrderState.CHECKING_DELETION);
            moveRemoteProviderOrdersToRemoteProviderOrdersList(this.checkingDeletionOrders);
            addOrdersToMap(this.checkingDeletionOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, OrderState.CHECKING_DELETION, this.activeOrdersMap.size()));
            addOrdersToMap(this.remoteProviderOrders, this.activeOrdersMap);
            LOGGER.info(String.format(Messages.Log.RECOVERING_LIST_OF_ORDERS_S_D, "REMOTE", this.activeOrdersMap.size()));
        } catch (Exception e) {
            throw new FatalErrorException(e.getMessage(), e);
        }
    }

    private void moveRemoteProviderOrdersToRemoteProviderOrdersList(SynchronizedDoublyLinkedList<Order> list) throws InternalServerErrorException {
        Order order;
        String localProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.PROVIDER_ID_KEY);

        while ((order = list.getNext()) != null) {
            if (order.isProviderRemote(localProviderId)) {
                list.removeItem(order);
                this.remoteProviderOrders.addItem(order);
            }
        }
        list.resetPointer();
    }

    private void addOrdersToMap(SynchronizedDoublyLinkedList<Order> ordersList, Map<String, Order> activeOrdersMap) {
        Order order;

        while ((order = ordersList.getNext()) != null) {
            activeOrdersMap.put(order.getId(), order);
        }
        ordersList.resetPointer();
    }

    public static SharedOrderHolders getInstance() {
        synchronized (SharedOrderHolders.class) {
            if (instance == null) {
                instance = new SharedOrderHolders();
            }
            return instance;
        }
    }

    public Map<String, Order> getActiveOrdersMap() {
        return this.activeOrdersMap;
    }

    public SynchronizedDoublyLinkedList<Order> getOpenOrdersList() {
        return this.openOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getSelectedOrdersList() {
        return this.selectedOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getSpawningOrdersList() {
        return this.spawningOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getFailedAfterSuccessfulRequestOrdersList() {
        return this.failedAfterSuccessfulRequestOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getFailedOnRequestOrdersList() {
        return this.failedOnRequestOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getFulfilledOrdersList() {
        return this.fulfilledOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getUnableToCheckStatusOrdersList() {
        return this.unableToCheckStatus;
    }

    public SynchronizedDoublyLinkedList<Order> getRemoteProviderOrdersList() {
        return this.remoteProviderOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getAssignedForDeletionOrdersList() {
        return this.assignedForDeletionOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getCheckingDeletionOrdersList() {
        return this.checkingDeletionOrders;
    }

    public SynchronizedDoublyLinkedList<Order> getOrdersList(OrderState orderState) {
        SynchronizedDoublyLinkedList<Order> list = null;
        switch (orderState) {
            case OPEN:
                list = SharedOrderHolders.getInstance().getOpenOrdersList();
                break;
            case SELECTED:
                list = SharedOrderHolders.getInstance().getSelectedOrdersList();
                break;
            case SPAWNING:
                list = SharedOrderHolders.getInstance().getSpawningOrdersList();
                break;
            case PENDING:
                list = SharedOrderHolders.getInstance().getRemoteProviderOrdersList();
                break;
            case FULFILLED:
                list = SharedOrderHolders.getInstance().getFulfilledOrdersList();
                break;
            case FAILED_AFTER_SUCCESSFUL_REQUEST:
                list = SharedOrderHolders.getInstance().getFailedAfterSuccessfulRequestOrdersList();
                break;
            case FAILED_ON_REQUEST:
                list = SharedOrderHolders.getInstance().getFailedOnRequestOrdersList();
                break;
            case UNABLE_TO_CHECK_STATUS:
                list = SharedOrderHolders.getInstance().getUnableToCheckStatusOrdersList();
                break;
            case ASSIGNED_FOR_DELETION:
                list = SharedOrderHolders.getInstance().getAssignedForDeletionOrdersList();
                break;
            case CHECKING_DELETION:
                list = SharedOrderHolders.getInstance().getCheckingDeletionOrdersList();
                break;
            default:
                break;
        }
        return list;
    }
}

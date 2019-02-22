package cloud.fogbow.ras.core.processors;

import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.OrderStateTransitioner;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnector;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.models.instances.InstanceState;
import cloud.fogbow.ras.core.models.linkedlists.ChainedList;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import org.apache.log4j.Logger;

public class OpenProcessor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(OpenProcessor.class);

    private String localMemberId;
    private ChainedList openOrdersList;
    /**
     * Attribute that represents the thread sleep time when there is no orders to be processed.
     */
    private Long sleepTime;

    public OpenProcessor(String localMemberId, String sleepTimeStr) {
        this.localMemberId = localMemberId;
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.openOrdersList = sharedOrderHolders.getOpenOrdersList();
        this.sleepTime = Long.valueOf(sleepTimeStr);
    }

    /**
     * Iterates over the open orders list and try to process one open order per time. The order
     * being null indicates that the iteration is in the end of the list or the list is empty.
     */
    @Override
    public void run() {
        boolean isActive = true;
        while (isActive) {
            try {
                Order order = this.openOrdersList.getNext();
                if (order != null) {
                    processOpenOrder(order);
                } else {
                    this.openOrdersList.resetPointer();
                    Thread.sleep(this.sleepTime);
                }
            } catch (InterruptedException e) {
                isActive = false;
                LOGGER.error(Messages.Error.THREAD_HAS_BEEN_INTERRUPTED, e);
            } catch (UnexpectedException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (Throwable e) {
                LOGGER.error(Messages.Error.UNEXPECTED_ERROR, e);
            }
        }
    }

    /**
     * Get an instance for an open order. If the method fails to get the instance, then the order is
     * set to failed, else, is set to spawning or pending if the order is localidentity or intercomponent,
     * respectively.
     */
    protected void processOpenOrder(Order order) throws UnexpectedException {
        // The order object synchronization is needed to prevent a race
        // condition on order access. For example: a user can delete an open
        // order while this method is trying to get an Instance for this order.
        synchronized (order) {
            OrderState orderState = order.getOrderState();
            // Check if the order is still in the Open state (it could have been changed by another thread)
            if (orderState.equals(OrderState.OPEN)) {
                try {
                    CloudConnector cloudConnector = CloudConnectorFactory.getInstance().
                            getCloudConnector(order.getProvider(), order.getCloudName());
                    String orderInstanceId = cloudConnector.requestInstance(order);
                    order.setInstanceId(orderInstanceId);
                    updateOrderStateAfterProcessing(order);
                } catch (Exception e) {
                    LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_INSTANCE_FROM_REQUEST, order), e);
                    order.setCachedInstanceState(InstanceState.FAILED);
                    OrderStateTransitioner.transition(order, OrderState.FAILED_ON_REQUEST);
                }
            }
        }
    }

    /**
     * Update the order state and do the order state transition after the open order process.
     */
    private void updateOrderStateAfterProcessing(Order order) throws UnexpectedException {
        if (order.isProviderLocal(this.localMemberId)) {
            String orderInstanceId = order.getInstanceId();

            if (orderInstanceId != null) {
                OrderStateTransitioner.transition(order, OrderState.SPAWNING);
            } else {
                throw new UnexpectedException(String.format(Messages.Exception.REQUEST_INSTANCE_NULL, order.getId()));
            }
        } else {
            OrderStateTransitioner.transition(order, OrderState.PENDING);
        }
    }
}
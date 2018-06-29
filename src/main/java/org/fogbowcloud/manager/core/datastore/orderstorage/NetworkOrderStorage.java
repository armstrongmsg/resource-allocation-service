package org.fogbowcloud.manager.core.datastore.orderstorage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.datastore.commands.SQLCommands;
import org.fogbowcloud.manager.core.models.linkedlist.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.models.orders.*;
import org.fogbowcloud.manager.core.models.token.FederationUser;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.Date;
import java.util.Map;

public class NetworkOrderStorage extends OrderStorage {

    private static final Logger LOGGER = Logger.getLogger(NetworkOrderStorage.class);

    public NetworkOrderStorage() throws SQLException {
        Statement statement = null;
        Connection connection = null;

        try {
            connection = getConnection();

            statement = connection.createStatement();

            statement.execute(SQLCommands.CREATE_NETWORK_ORDER_TABLE_SQL);

            statement.close();
        } catch (SQLException e) {
            LOGGER.error("Error creating order table", e);
            throw new SQLException(e);
        } finally {
            closeConnection(statement, connection);
        }
    }

    public void addOrder(Order order) {
        NetworkOrder networkOrder = (NetworkOrder) order;

        Connection connection = null;
        PreparedStatement orderStatement = null;

        try {
            connection = getConnection();
            connection.setAutoCommit(false);

            orderStatement = connection.prepareStatement(SQLCommands.INSERT_NETWORK_ORDER_SQL);

            addOverallOrderAttributes(orderStatement, networkOrder);

            orderStatement.setString(8, networkOrder.getGateway());
            orderStatement.setString(9, networkOrder.getAddress());
            orderStatement.setString(10, networkOrder.getAllocation().getValue());
            orderStatement.setTimestamp(11, new Timestamp(new Date().getTime()));

            orderStatement.executeUpdate();

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Couldn't create order.", e);
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                LOGGER.error("Couldn't rollback transaction.", e1);
            }
        } finally {
            closeConnection(orderStatement, connection);
        }
    }

    public void updateOrder(Order order) {
        NetworkOrder networkOrder = (NetworkOrder) order;

        Connection connection = null;
        PreparedStatement orderStatement = null;

        try {
            connection = getConnection();
            connection.setAutoCommit(false);

            orderStatement = connection.prepareStatement(SQLCommands.UPDATE_NETWORK_ORDER_SQL);

            orderStatement.setString(1, networkOrder.getInstanceId());
            orderStatement.setString(2, networkOrder.getOrderState().name());
            orderStatement.setString(3, networkOrder.getId());

            orderStatement.executeUpdate();

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Couldn't create order.", e);
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                LOGGER.error("Couldn't rollback transaction.", e1);
            }
        } finally {
            closeConnection(orderStatement, connection);
        }
    }

    public void readOrdersByState(
            OrderState orderState, SynchronizedDoublyLinkedList synchronizedDoublyLinkedList) {

        Connection connection = null;
        PreparedStatement orderStatement = null;

        try {
            connection = getConnection();
            connection.setAutoCommit(false);

            orderStatement = connection.prepareStatement(SQLCommands.SELECT_VOLUME_ORDER_SQL);
            orderStatement.setString(1, orderState.name());

            ResultSet volumeResult = orderStatement.executeQuery();

            while (volumeResult.next()) {
                volumeResult.getString(1);

                Map<String, String> federationUserAttr = getFederationUserAttrFromString(volumeResult.getString(5));

                VolumeOrder volumeOrder = new VolumeOrder(volumeResult.getString(1),
                        new FederationUser(volumeResult.getString(4), federationUserAttr),
                        volumeResult.getString(6), volumeResult.getString(7),
                        volumeResult.getInt(8));

                volumeOrder.setInstanceId(volumeResult.getString(2));
                volumeOrder.setOrderState(OrderState.fromValue(volumeResult.getString(3)));

                synchronizedDoublyLinkedList.addItem(volumeOrder);
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Couldn't create order.", e);
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                LOGGER.error("Couldn't rollback transaction.", e1);
            }
        } finally {
            closeConnection(orderStatement, connection);
        }
    }

    private void addOverallOrderAttributes(PreparedStatement orderStatement, Order order) throws SQLException {
        orderStatement.setString(1, order.getId());
        orderStatement.setString(2, order.getInstanceId());
        orderStatement.setString(3, order.getOrderState().name());
        orderStatement.setString(4, order.getFederationUser().getId());

        Gson gson = new Gson();
        String fedAttributes = gson.toJson(order.getFederationUser().getAttributes());

        orderStatement.setString(5, fedAttributes);
        orderStatement.setString(6, order.getRequestingMember());
        orderStatement.setString(7, order.getProvidingMember());
    }

    private Map<String, String> getFederationUserAttrFromString(String jsonString) {
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>(){}.getType();

        return gson.fromJson(jsonString, mapType);
    }
}

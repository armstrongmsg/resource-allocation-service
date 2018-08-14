package org.fogbowcloud.manager.core.plugins.cloud.openstack.network.v2;

import com.google.gson.annotations.SerializedName;
import org.fogbowcloud.manager.util.GsonHolder;

import static org.fogbowcloud.manager.core.plugins.cloud.openstack.OpenstackRestApiConstants.Network.*;

/**
 * Documentation: https://developer.openstack.org/api-ref/network/v2/
 *
 * Response Example:
 * {
 *     "subnet": {
 *         "gateway_ip": "192.0.0.1",
 *         "enable_dhcp": true,
 *         "cidr": "192.0.0.0/8"
 *     }
 * }
 *
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class GetSubnetResponse {

    @SerializedName(SUBNET_KEY_JSON)
    private Subnet subnet;

    public static GetSubnetResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, GetSubnetResponse.class);
    }

    public static class Subnet {

        @SerializedName(GATEWAY_IP_KEY_JSON)
        private String gatewayIp;

        @SerializedName(ENABLE_DHCP_KEY_JSON)
        private boolean dhcpEnabled;

        @SerializedName(CIDR_KEY_JSON)
        private String subnetAddress;

    }

    public String getGatewayIp() {
        return subnet.gatewayIp;
    }

    public boolean isDhcpEnabled() {
        return subnet.dhcpEnabled;
    }

    public String getSubnetAddress() {
        return subnet.subnetAddress;
    }

}

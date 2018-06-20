package org.fogbowcloud.manager.api.http;

import java.util.List;

import org.fogbowcloud.manager.core.intercomponent.exceptions.RemoteRequestException;
import org.fogbowcloud.manager.core.ApplicationFacade;
import org.fogbowcloud.manager.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.exceptions.PropertyNotSpecifiedException;
import org.fogbowcloud.manager.core.exceptions.RequestException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.plugins.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.plugins.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.orders.NetworkOrder;
import org.fogbowcloud.manager.core.models.instances.NetworkInstance;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = NetworkOrdersController.NETWORK_ENDPOINT)
public class NetworkOrdersController {

    public static final String NETWORK_ENDPOINT = "networks";
    
    public static final String FEDERATION_TOKEN_VALUE_HEADER_KEY = "federationTokenValue";

    private final Logger LOGGER = Logger.getLogger(NetworkOrdersController.class);
    
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<String> createNetwork(@RequestBody NetworkOrder networkOrder,
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
        throws UnauthenticatedException, UnauthorizedException, OrderManagementException {
        LOGGER.info("New network order request received.");

        String networkId = ApplicationFacade.getInstance().createNetwork(networkOrder, federationTokenValue);
        return new ResponseEntity<String>(networkId, HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<List<NetworkInstance>> getAllNetworks(
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
        throws UnauthenticatedException, TokenCreationException, RequestException, PropertyNotSpecifiedException, UnauthorizedException, InstanceNotFoundException, RemoteRequestException {
        List<NetworkInstance> networks = ApplicationFacade.getInstance().getAllNetworks(federationTokenValue);
        return new ResponseEntity<>(networks, HttpStatus.OK);
    }

    @RequestMapping(value = "/{networkId}", method = RequestMethod.GET)
    public ResponseEntity<NetworkInstance> getNetwork(@PathVariable String networkId,
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
        throws UnauthenticatedException, TokenCreationException, RequestException, PropertyNotSpecifiedException, UnauthorizedException, InstanceNotFoundException, RemoteRequestException {
        NetworkInstance networkInstance = ApplicationFacade.getInstance().getNetwork(networkId, federationTokenValue);
        return networkInstance == null ?  new ResponseEntity<>(networkInstance, HttpStatus.NOT_FOUND) :
                new ResponseEntity<>(networkInstance, HttpStatus.OK);
    }

    @RequestMapping(value = "/{networkId}", method = RequestMethod.DELETE)
    public ResponseEntity<Boolean> deleteNetwork(@PathVariable String networkId,
            @RequestHeader(value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
        throws UnauthenticatedException, UnauthorizedException, OrderManagementException {
        ApplicationFacade.getInstance().deleteNetwork(networkId, federationTokenValue);
        return new ResponseEntity<Boolean>(HttpStatus.NO_CONTENT);
    }

}

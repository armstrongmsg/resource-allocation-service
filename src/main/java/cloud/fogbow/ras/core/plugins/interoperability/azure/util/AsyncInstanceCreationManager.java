package cloud.fogbow.ras.core.plugins.interoperability.azure.util;

import cloud.fogbow.ras.constants.Messages;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
This class helps to cope Plugin that it has asynchronous instances creation.

Problem: It might generate resource trash in the cloud due to the fact that
If the RAS shutdown this class will lost its data in memory.

Note: This context helps to fix this issue: https://github.com/fogbow/resource-allocation-service/issues/435
 */
public class AsyncInstanceCreationManager {

    private static final Logger LOGGER = Logger.getLogger(AsyncInstanceCreationManager.class);

    private final static List<String> creating =  Collections.synchronizedList(new ArrayList<>());

    /*
    It must be used soon before the plugin makes asynchronous creation operation in the cloud.

    @return It is a callback that it must be used when the cloud finish asynchronous creation operation in the cloud;
    Suggestion: Use "finishCreationCallback" as attribute name.
       */
    public Runnable startCreation(String instanceId) {
        LOGGER.debug(String.format(Messages.Info.START_ASYNC_INSTANCE_CREATION_S, instanceId));
        defineAsCreating(instanceId);
        return () -> {
            defineAsCreated(instanceId);
            LOGGER.debug(String.format(Messages.Info.END_ASYNC_INSTANCE_CREATION_S, instanceId));
        };
    }

    /*
    It must check if the instance is still creating by the asynchronous creation operation in the cloud.
    */
    public boolean isCreating(String instanceId) {
        return this.creating.contains(instanceId);
    }

    /*
    It must add the instanceId in "Creating" list when the resource is not created in the cloud.
     */
    private void defineAsCreating(String instanceId) {
        this.creating.add(instanceId);
    }

    /*
    It must remove the instanceId in "Creating" list when the resource is created in the cloud.
    */
    private void defineAsCreated(String instanceId) {
        this.creating.remove(instanceId);
    }

}
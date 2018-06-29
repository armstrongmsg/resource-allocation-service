package org.fogbowcloud.manager.core.plugins;

import java.lang.reflect.Constructor;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.exceptions.FatalErrorException;

public class PluginFactory {

    private static final Logger LOGGER = Logger.getLogger(PluginFactory.class.getName());
    private static final int EXIT_ERROR_CODE = 128;

    public Object createPluginInstance(String pluginClassName) throws FatalErrorException {

        Object pluginInstance = null;

        Class<?> classpath;
        Constructor<?> constructor;

        try {
            classpath = Class.forName(pluginClassName);
            constructor = classpath.getConstructor();
            pluginInstance = constructor.newInstance();
        } catch (ClassNotFoundException e) {
            String msg = "No " + pluginClassName
                    + " class under this repository. Please inform a valid class.";
            throw new FatalErrorException(msg);
        } catch (Exception e) {
            throw new FatalErrorException(e.getMessage(), e);
        }

        return pluginInstance;
    }
}

package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.plugins.cloud.*;

public class CloudPluginsHolder {
    private final AttachmentPlugin attachmentPlugin;
    private final ComputePlugin computePlugin;
    private final ComputeQuotaPlugin computeQuotaPlugin;
    private final NetworkPlugin networkPlugin;
    private final VolumePlugin volumePlugin;
    private final ImagePlugin imagePlugin;

    public CloudPluginsHolder(PluginInstantiator instantiationInitService) {

        this.attachmentPlugin = instantiationInitService.getAttachmentPlugin();
        this.computePlugin = instantiationInitService.getComputePlugin();
        this.computeQuotaPlugin = instantiationInitService.getComputeQuotaPlugin();
        this.networkPlugin = instantiationInitService.getNetworkPlugin();
        this.volumePlugin = instantiationInitService.getVolumePlugin();
        this.imagePlugin = instantiationInitService.getImagePlugin();
    }

    public AttachmentPlugin getAttachmentPlugin() {
        return this.attachmentPlugin;
    }

    public ComputePlugin getComputePlugin() {
        return this.computePlugin;
    }

    public ComputeQuotaPlugin getComputeQuotaPlugin() {
        return this.computeQuotaPlugin;
    }

    public NetworkPlugin getNetworkPlugin() {
        return this.networkPlugin;
    }

    public VolumePlugin getVolumePlugin() {
        return this.volumePlugin;
    }

    public ImagePlugin getImagePlugin() {
        return this.imagePlugin;
    }
}

package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9;

import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackRequest;

public class GetAllDiskOfferingsRequest extends CloudStackRequest {
    public static final String LIST_DISK_OFFERINGS_COMMAND = "listDiskOfferings";

    protected GetAllDiskOfferingsRequest(Builder builder) throws InvalidParameterException {
        super(builder.cloudStackUrl);
    }

    @Override
    public String getCommand() {
        return LIST_DISK_OFFERINGS_COMMAND;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static class Builder {
        private String cloudStackUrl;

        public GetAllDiskOfferingsRequest build(String cloudStackUrl) throws InvalidParameterException {
            this.cloudStackUrl = cloudStackUrl;
            return new GetAllDiskOfferingsRequest(this);
        }
    }
}
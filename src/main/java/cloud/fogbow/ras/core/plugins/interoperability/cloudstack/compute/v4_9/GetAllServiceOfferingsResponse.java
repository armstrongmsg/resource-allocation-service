package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import cloud.fogbow.common.util.GsonHolder;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import static cloud.fogbow.common.constants.CloudStackConstants.Compute.*;


/**
 * Documentation: https://cloudstack.apache.org/api/apidocs-4.9/apis/listVirtualMachines.html
 * <p>
 * Response example:
 * {
 * "listserviceofferingsresponse": {
 * "count": 1,
 * "serviceoffering": [{
 * "id": "97637962-2244-4159-b72c-120834757514",
 * "name": "offering-name",
 * "cpunumber": 4,
 * "memory": 6144,
 * "tags": "tag1:value1,tag2:value2",
 * }]
 * }
 * }
 */
public class GetAllServiceOfferingsResponse {
    @SerializedName(LIST_SERVICE_OFFERINGS_KEY_JSON)
    private ListServiceOfferingsResponse response;

    public List<ServiceOffering> getServiceOfferings() {
        return response.serviceOfferings;
    }

    public static GetAllServiceOfferingsResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, GetAllServiceOfferingsResponse.class);
    }

    public class ListServiceOfferingsResponse {
        @SerializedName(SERVICE_OFFERING_KEY_JSON)
        private List<ServiceOffering> serviceOfferings;

    }

    public class ServiceOffering {
        @SerializedName(ID_KEY_JSON)
        private String id;
        @SerializedName(NAME_KEY_JSON)
        private String name;
        @SerializedName(CPU_NUMBER_KEY_JSON)
        private int cpuNumber;
        @SerializedName(MEMORY_KEY_JSON)
        private int memory;
        @SerializedName(TAGS_KEY_JSON)
        private String tags;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getCpuNumber() {
            return cpuNumber;
        }

        /**
         * @return the memory of this service offering in MB
         */
        public int getMemory() {
            return memory;
        }

        public String getTags() {
            return tags;
        }
    }
}
package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.attachment.v4_9;

import cloud.fogbow.common.util.GsonHolder;
import com.google.gson.annotations.SerializedName;

import static cloud.fogbow.common.constants.CloudStackConstants.Attachment.ATTACH_VOLUME_KEY_JSON;
import static cloud.fogbow.common.constants.CloudStackConstants.Attachment.JOB_ID_KEY_JSON;

/**
 * Documentation: https://cloudstack.apache.org/api/apidocs-4.9/apis/attachVolume.html
 * <p>
 * Response example:
 * {
 *  "attachvolumeresponse":{
 *      "jobid":"1ad02fda-7ec2-4130-b390-068c80699432"
 *      }
 * }
 * <p>
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class AttachVolumeResponse {

    @SerializedName(ATTACH_VOLUME_KEY_JSON)
    private AttachResponse response;
    
    public class AttachResponse {
        
        @SerializedName(JOB_ID_KEY_JSON)
        private String jobId;
        
    }

    public static AttachVolumeResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, AttachVolumeResponse.class);
    }

    public String getJobId() {
        return response.jobId;
    }

}
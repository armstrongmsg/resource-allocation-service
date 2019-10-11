package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.attachment.v4_9;

import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackCloudUtils;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudstackTestUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

public class AttachmentJobStatusResponseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    // test case: When calling the fromJson method, it must verify
    // if It returns the right AttachmentJobStatusResponse.
    @Test
    public void testFromJsonSuccessfully() throws IOException {
        // set up
        String jobId = "jobId";
        int jobStatus = CloudStackCloudUtils.JOB_STATUS_COMPLETE;
        String volumeId = "volumeId";
        int deviceId = 2;
        String state = "state";
        String virtualMachineId = "virtualMachineId";
        String attachmentJobStatusResponseJson = CloudstackTestUtils.attachmentJobStatusResponseJson(
                jobStatus, volumeId, deviceId, virtualMachineId, state, jobId);

        // execute
        AttachmentJobStatusResponse attachmentJobStatusResponse =
                AttachmentJobStatusResponse.fromJson(attachmentJobStatusResponseJson);

        // verify
        Assert.assertEquals(jobStatus, attachmentJobStatusResponse.getJobStatus());
        AttachmentJobStatusResponse.Volume volume = attachmentJobStatusResponse.getVolume();
        Assert.assertEquals(volumeId, volume.getId());
        Assert.assertEquals(jobId, volume.getJobId());
        Assert.assertEquals(virtualMachineId, volume.getVirtualMachineId());
        Assert.assertEquals(state, volume.getState());
        Assert.assertEquals(deviceId, volume.getDeviceId());
    }

    // test case: When calling the fromJson method with error json response,
    // it must verify if It throws a HttpResponseException.
    @Test
    public void testFromJsonFail() throws IOException {
        // set up
        String errorText = "anyString";
        int jobStatus = CloudStackCloudUtils.JOB_STATUS_FAILURE;
        int errorCode = HttpStatus.SC_BAD_REQUEST;
        String volumesErrorResponseJson = CloudstackTestUtils
                .asyncErrorResponseJson(jobStatus, errorCode, errorText);

        // verify
        this.expectedException.expect(HttpResponseException.class);
        this.expectedException.expectMessage(errorText);

        // execute
        AttachmentJobStatusResponse.fromJson(volumesErrorResponseJson);
    }

}

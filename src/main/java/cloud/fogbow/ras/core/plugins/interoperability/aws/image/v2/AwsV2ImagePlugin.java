package cloud.fogbow.ras.core.plugins.interoperability.aws.image.v2;

import java.util.*;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.AwsV2User;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.ImageInstance;
import cloud.fogbow.ras.api.http.response.ImageSummary;
import cloud.fogbow.ras.core.plugins.interoperability.ImagePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.aws.AwsV2ClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.aws.AwsV2ConfigurationPropertyKeys;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;

public class AwsV2ImagePlugin implements ImagePlugin<AwsV2User> {

	private static final Integer FIRST_POSITION = 0;
    private static final Integer NO_VALUE_FLAG = -1;
	private static final int ONE_THOUSAND_BYTES = 1024;

    private String region;

    public AwsV2ImagePlugin(String confFilePath) {
        Properties properties = PropertiesUtil.readProperties(confFilePath);
        this.region = properties.getProperty(AwsV2ConfigurationPropertyKeys.AWS_REGION_SELECTION_KEY);
    }

    @Override
    public List<ImageSummary> getAllImages(AwsV2User cloudUser) throws FogbowException {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
        		.owners(cloudUser.getId())
        		.build();
        
        Ec2Client client = AwsV2ClientUtil.createEc2Client(cloudUser.getToken(), this.region);
        DescribeImagesResponse imagesResponse = client.describeImages(request);
        
        List<ImageSummary> images = new ArrayList<>();
        List<software.amazon.awssdk.services.ec2.model.Image> retrievedImages = imagesResponse.images();
        for (software.amazon.awssdk.services.ec2.model.Image image : retrievedImages) {
            images.add(new ImageSummary(image.imageId(), image.name()));
        }
        return images;
    }

    @Override
    public ImageInstance getImage(String imageId, AwsV2User cloudUser) throws FogbowException {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
        		.imageIds(imageId)
        		.build();

        Ec2Client client = AwsV2ClientUtil.createEc2Client(cloudUser.getToken(), this.region);
        DescribeImagesResponse response = client.describeImages(request);

        ImageInstance image = null;
        if (!response.images().isEmpty()) {
        	software.amazon.awssdk.services.ec2.model.Image retrievedImage = getFirstImage(response);
        	image = mountImage(retrievedImage);
        }
        return image;
    }

	protected software.amazon.awssdk.services.ec2.model.Image getFirstImage(DescribeImagesResponse response) {
			return response.images().get(FIRST_POSITION);
	}

	protected ImageInstance mountImage(software.amazon.awssdk.services.ec2.model.Image awsImage) {
        String id = awsImage.imageId();
        String name = awsImage.name();
		String status = awsImage.stateAsString();
		long size = getSize(awsImage.blockDeviceMappings());
		long minDisk = NO_VALUE_FLAG;
		long minRam = NO_VALUE_FLAG;
        return new ImageInstance(id, name, size, minDisk, minRam, status);
    }

    protected long getSize(List<BlockDeviceMapping> blockDeviceMappings) {
    	long kilobyte = ONE_THOUSAND_BYTES;
    	long megabyte = kilobyte * ONE_THOUSAND_BYTES;
    	long gigabyte = megabyte * ONE_THOUSAND_BYTES;
    	long size = 0;
        for (BlockDeviceMapping blockDeviceMapping : blockDeviceMappings) {
            size += blockDeviceMapping.ebs().volumeSize();
        }
        return size * gigabyte;
    }

}

package org.fogbowcloud.ras.core.plugins.interoperability.opennebula.compute.v5_4;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "DISK")
public class VirtualMachineImageDisk {

	private String imageId;

	@XmlElement(name = "IMAGE_ID")
	public String getImageId() {
		return imageId;
	}

	public void setImageId(String imageId) {
		this.imageId = imageId;
	}
	
}

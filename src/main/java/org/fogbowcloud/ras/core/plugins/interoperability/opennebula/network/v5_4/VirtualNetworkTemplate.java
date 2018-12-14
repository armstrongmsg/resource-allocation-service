package org.fogbowcloud.ras.core.plugins.interoperability.opennebula.network.v5_4;

import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.AR;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.BRIDGE;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.DESCRIPTION;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.IP;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.NAME;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.NETWORK_ADDRESS;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.NETWORK_GATEWAY;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.SECURITY_GROUP;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.SIZE;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.TEMPLATE;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.TYPE;
import static org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaTagNameConstants.VIRTUAL_NETWORK_BRIDGED_DRIVE;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaMarshallerTemplate;




@XmlRootElement(name = TEMPLATE)
public class VirtualNetworkTemplate extends OpenNebulaMarshallerTemplate {

	private String name;
	private String description;
	private String type;
	private String bridge;
	private String bridgedDrive;
	private String networkAddress;
	private String networkGateway;
	private String securityGroupId;
	private VirtualNetworkTemplate.AddressRange addressRange;
	
	@XmlElement(name = NAME)
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	@XmlElement(name = DESCRIPTION)
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	@XmlElement(name = TYPE)
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	@XmlElement(name = BRIDGE)
	public String getBridge() {
		return bridge;
	}
	
	public void setBridge(String bridge) {
		this.bridge = bridge;
	}
	
	@XmlElement(name = VIRTUAL_NETWORK_BRIDGED_DRIVE)
	public String getBridgedDrive() {
		return bridgedDrive;
	}

	public void setBridgedDrive(String bridgedDrive) {
		this.bridgedDrive = bridgedDrive;
	}
	
	@XmlElement(name = NETWORK_ADDRESS)
	public String getNetworkAddress() {
		return networkAddress;
	}
	
	public void setNetworkAddress(String networkAddress) {
		this.networkAddress = networkAddress;
	}
	
	@XmlElement(name = NETWORK_GATEWAY)
	public String getNetworkGateway() {
		return networkGateway;
	}
	
	public void setNetworkGateway(String networkGateway) {
		this.networkGateway = networkGateway;
	}
	
	@XmlElement(name = SECURITY_GROUP)
	public String getSecurityGroupId() {
		return securityGroupId;
	}

	public void setSecurityGroupId(String securityGroupId) {
		this.securityGroupId = securityGroupId;
	}
	
	@XmlElement(name = AR)
	public VirtualNetworkTemplate.AddressRange getAddressRange() {
		return addressRange;
	}
	
	public void setAddressRange(VirtualNetworkTemplate.AddressRange addressRange) {
		this.addressRange = addressRange;
	}
	
	@XmlRootElement(name = AR)
	public static class AddressRange {

		private String type;
		private String ipAddress;
		private String rangeSize;
		
		@XmlElement(name = TYPE)
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		@XmlElement(name = IP)
		public String getIpAddress() {
			return ipAddress;
		}
		
		public void setIpAddress(String ipAddress) {
			this.ipAddress = ipAddress;
		}
		
		@XmlElement(name = SIZE)
		public String getRangeSize() {
			return rangeSize;
		}
		
		public void setRangeSize(String rangeSize) {
			this.rangeSize = rangeSize;
		}
	}
}

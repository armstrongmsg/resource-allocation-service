package org.fogbowcloud.manager.core.plugins.serialization.openstack;

// TODO review the same keys
public class OpenstackRestApiConstants {
	
	public static class Volume {
		public static final String VOLUME_KEY_JSON = "volume";		
		public static final String STATUS_KEY_JSON = "status";
		public static final String SIZE_KEY_JSON = "size";
		public static final String NAME_KEY_JSON = "name";
		public static final String ID_KEY_JSON = "id";
	}
	
	public static class Attachment {

		public static final String VOLUME_ATTACHMENT_KEY_JSON = "volumeAttachment";
		public static final String VOLUME_ID_KEY_JSON = "volumeId";
		public static final String SERVER_ID_KEY_JSON = "serverId";
		public static final String ID_KEY_JSON = "id";
		public static final String DEVICE_KEY_JSON = "device";
		
	}

	public static class Compute {

		public static final String SERVER_KEY_JSON = "server";
		public static final String ID_KEY_JSON = "id";
		public static final String NAME_KEY_JSON = "name";
		public static final String DISK_KEY_JSON = "disk";
		public static final String MEMORY_KEY_JSON = "ram";
		public static final String VCPUS_KEY_JSON = "vcpus";

		public static final String ADDRESSES_KEY_JSON = "addresses";
		public static final String FLAVOR_KEY_JSON = "flavor";
		public static final String STATUS_KEY_JSON = "status";
		public static final String PROVIDER_KEY_JSON = "provider";
		public static final String ADDRESS_KEY_JSON = "addr";

		public static final String IMAGE_REFERENCE_KEY_JSON = "imageRef";
		public static final String FLAVOR_REFERENCE_KEY_JSON = "flavorRef";
		public static final String USER_DATA_KEY_JSON = "user_data";
		public static final String KEY_NAME_KEY_JSON = "key_name";
		public static final String NETWORKS_KEY_JSON = "networks";
		public static final String SECURITY_GROUPS_KEY_JSON = "security_groups";
		public static final String UUID_KEY_JSON = "uuid";

	}
	
	public static class Image {
	
		public static final String ID_KEY_JSON = "id";
		public static final String NAME_KEY_JSON = "name";
		public static final String SIZE_KEY_JSON = "size";
		public static final String STATUS_KEY_JSON = "status";
		public static final String MIN_RAM_KEY_JSON = "min_ram";
		public static final String MIN_DISK_KEY_JSON = "min_disk";		
		public static final String VISIBILITY_KEY_JSON = "visibility";
		public static final String  OWNER_KEY_JSON = "owner";
		public static final String  NEXT_KEY_JSON = "next";
		public static final String  IMAGES_KEY_JSON = "images";
		
		
	}
}

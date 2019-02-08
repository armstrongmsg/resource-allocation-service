package cloud.fogbow.ras.core.plugins.interoperability.opennebula;

import cloud.fogbow.ras.constants.Messages;
import org.apache.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

public class OpenNebulaMarshallerTemplate {

	private final static Logger LOGGER = Logger.getLogger(OpenNebulaMarshallerTemplate.class);
	
	public String marshalTemplate() {
		StringWriter writer = new StringWriter();
		String xml = null;
	    try {
	        JAXBContext context = JAXBContext.newInstance(this.getClass());
	        Marshaller marshaller = context.createMarshaller();
	        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
	        marshaller.marshal(this, writer);
	        xml = writer.toString();
	    } catch (JAXBException e) {
	        LOGGER.error(Messages.Error.ERROR_WHILE_CREATING_REQUEST_BODY);
	    }
	    return xml;
	}
}

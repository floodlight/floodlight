package net.floodlightcontroller.routing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.routing.VirtualGatewayInterface;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;

/**
 * @author Qing Wang (qw@g.clemson.edu) at 12/29/17
 */
public class VirtualInterfaceResource extends ServerResource {

    @Put
    @Post
    // This also includes overwrite an existing interface
    public void addVirtualInterface(String jsonData) throws IOException {


        try {
            VirtualGatewayInterface vInterface = new ObjectMapper()
                    .reader(VirtualGatewayInterface.class)
                    .readValue(jsonData);

        }
        catch (IOException e) {
            throw new IOException(e);
        }

    }


    @Delete
    public void deleteVirtualInterface(String jsonData) throws IOException {


    }

}

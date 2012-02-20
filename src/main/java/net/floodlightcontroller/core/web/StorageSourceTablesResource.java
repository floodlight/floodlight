package net.floodlightcontroller.core.web;

import java.util.Set;

import net.floodlightcontroller.storage.IStorageSourceService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class StorageSourceTablesResource extends ServerResource {
    @Get("json")
    public Set<String> retrieve() {
        IStorageSourceService storageSource = (IStorageSourceService)getContext().
                getAttributes().get(IStorageSourceService.class.getCanonicalName());
        Set<String> allTableNames = storageSource.getAllTableNames();
        return allTableNames;
    }
}

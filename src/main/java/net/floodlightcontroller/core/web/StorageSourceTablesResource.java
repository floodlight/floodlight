package net.floodlightcontroller.core.web;

import java.util.Set;

import net.floodlightcontroller.storage.IStorageSource;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class StorageSourceTablesResource extends ServerResource {
    @Get("json")
    public Set<String> retrieve() {
        IStorageSource storageSource = (IStorageSource)getContext().getAttributes().get("storageSource");
        Set<String> allTableNames = storageSource.getAllTableNames();
        return allTableNames;
    }
}

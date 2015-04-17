package net.floodlightcontroller.core.module;

public class FloodlightModuleConfigFileNotFoundException extends
    FloodlightModuleException {
    private static final long serialVersionUID = 1L;

    public FloodlightModuleConfigFileNotFoundException(String fileName) {
        super("No such file or directory: " + fileName);
    }
}

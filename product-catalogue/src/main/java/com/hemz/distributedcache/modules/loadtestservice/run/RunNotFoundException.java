package com.hemz.distributedcache.modules.loadtestservice.run;

public class RunNotFoundException extends RuntimeException {
    public RunNotFoundException(long id) {
        super("No run with id " + id);
    }
}

package com.netflix.spectator.jvm;

import java.util.ArrayList;
import java.util.List;

class CompositeAutoCloseable implements AutoCloseable {
    private final List<AutoCloseable> autoCloseables = new ArrayList<>();

    public void add(AutoCloseable autoCloseable) {
        autoCloseables.add(autoCloseable);
    }

    @Override
    public void close() throws Exception {
        Exception firstException = null;
        for (AutoCloseable autoCloseable : autoCloseables) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                if (firstException != null) {
                    firstException.addSuppressed(e);
                } else {
                    firstException = e;
                }
            }
        }
        if (firstException != null) throw firstException;
    }
}

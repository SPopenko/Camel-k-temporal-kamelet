package org.apache.camel.component.temporal.sample;

import org.apache.camel.main.Main;

public final class TemporalHttpApp {

    private TemporalHttpApp() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().httpServer()
            .withEnabled(true)
            .withHost("0.0.0.0")
            .withPort(8080);
        main.run(args);
    }
}

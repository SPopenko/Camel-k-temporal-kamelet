package org.apache.camel.component.temporal.e2e.route;

import org.apache.camel.main.Main;

public final class RouteRunnerMain {

    private RouteRunnerMain() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new TemporalKameletRoute());
        main.run(args);
    }
}

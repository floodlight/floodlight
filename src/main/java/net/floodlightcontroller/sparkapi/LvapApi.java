package net.floodlightcontroller.sparkapi;

import java.util.HashMap;
import static spark.Spark.*;
import com.google.gson.*;

public class LvapApi {

    private HashMap lvapDb;
    private Gson g;

    public LvapApi() {
        this.lvapDb = new HashMap();
        this.g = new Gson();
    }

    private void setupEndpoints() {
        post("/lvap", (request, response) -> {
            // check if entry already exists

            //this.lvapDb.put()
            return request.body();
        });

        put("/lvap", (request, response) -> {
            //this.lvapDb.
            return request.body();

        });

    }
}

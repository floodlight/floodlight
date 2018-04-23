package net.floodlightcontroller.sparkapi;

import java.util.*;

import static spark.Spark.*;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LvapApi {

    private HashMap <String, Lvap> lvapDb;
    private static final Logger logger = LoggerFactory.getLogger(LvapApi.class);

    public LvapApi() {
        this.lvapDb = new HashMap<>();
        this.setupEndpoints();
    }

    private void setupEndpoints() {
        logger.info("Starting LVAP API..");
        initExceptionHandler( e -> logger.error ("ERROR " + e.getMessage()) );

        get("/lvap", (request, response) -> {
            ArrayList<Lvap> list = new ArrayList<>();
            for (Map.Entry<String, Lvap> item : lvapDb.entrySet()){
                list.add(item.getValue());
            }
            response.status(200);
            response.header("Content-type", "application/json");
            response.body(new Gson().toJson(list));
            return response.body();
        });

        post("/lvap", (request, response) -> {
            try {
                Lvap requestObject = new Gson().fromJson(request.body(), Lvap.class);

                if (!this.lvapDb.containsKey(requestObject.getMac())) {
                    // generate BSSID
                    requestObject.setBssid("11:11:11:11:11:11");
                    this.lvapDb.put(requestObject.getMac(), requestObject);
                    response.body(requestObject.toJson());
                    response.status(200);
                    response.header("Content-type", "application/json");
                } else {
                    // duplicate POST
                    response.status(409); // Conflict
                    response.body("");
                }
            } catch (Exception e){
                response.status(500);
                response.body("Exception: " + e.getMessage());
            }
            return response.body();
        });

        put("/lvap", (request, response) -> {
            try {
                Lvap requestObject = new Gson().fromJson(request.body(), Lvap.class);
                if (lvapDb.containsKey(requestObject.getMac())) {
                    Lvap db = this.lvapDb.get(requestObject.getMac());
                    db.setIp(requestObject.getIp());
                    this.lvapDb.replace(db.getMac(), db);
                    response.status(200);
                    response.header("Content-type", "application/json");
                    response.body("");
                } else {
                    response.status(404);
                }
            } catch (Exception e){
                response.status(500);
                response.body("Exception: " + e.getMessage());
            }
            return response.body();
        });

        delete("/lvap", (request, response) -> {
            try {
                Lvap requestObject = new Gson().fromJson(request.body(), Lvap.class);

                if (lvapDb.containsKey(requestObject.getMac())) {
                    response.body(this.lvapDb.get(requestObject.getMac()).toJson());
                    this.lvapDb.remove(requestObject.getMac());
                    response.status(200);
                    response.header("Content-type", "application/json");
                } else {
                    response.status(404);
                }

            } catch (Exception e){
                response.status(500);
                response.body("Exception: " + e.getMessage());
            }
            return response.body();
        });

    }
}

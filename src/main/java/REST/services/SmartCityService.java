package REST.services;

import REST.beans.Drone;
import REST.beans.SmartCity;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("smartcity")
public class SmartCityService {

    //restituisce la lista di Droni nella smartCity
    @GET
    @Produces({"application/json", "application/xml"})
    public Response getSmartCity(){
        return Response.ok(SmartCity.getInstance()).build();
    }

    @Path("add")
    @POST
    @Consumes({"application/json"})
    public Response addDrone(Drone drone){
        SmartCity.getInstance().addDrone(drone);
        String result = "Drone aggiunto: " + drone;
        return Response.status(201).entity(result).build();
    }

    @DELETE
    @Consumes({"application/json"})
    public Response deleteDrone(Drone drone){
        SmartCity.getInstance().deleteDrone(drone);
        return Response.ok().build();
    }
}

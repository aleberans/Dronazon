package REST.services;

import REST.beans.Drone;
import REST.beans.SmartCity;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("smartcity")
public class SmartCityService {

    //restituisce la lista di Droni della smartCity
    @GET
    @Produces({"application/json"})
    public Response getSmartCityInformation(){
        return Response.ok(SmartCity.getInstance()).build();
    }

    @Path("add")
    @POST
    @Produces({"application/json"})
    public Response addDrone(Drone drone){
        GenericEntity<List<Drone>> entity = new GenericEntity<List<Drone>>(SmartCity.getInstance().addDrone(drone)) {
        };
        return Response.ok(entity).build();
    }

    @DELETE
    @Consumes({"application/json"})
    public Response deleteDrone(Drone drone){
        SmartCity.getInstance().deleteDrone(drone);
        return Response.ok().build();
    }
}

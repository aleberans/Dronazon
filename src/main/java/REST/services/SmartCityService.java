package REST.services;

import DronazonPackage.DroneClient;
import REST.beans.Drone;
import REST.beans.SmartCity;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;


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

    @Path("remove/{idDrone}")
    @DELETE
    @Consumes({"application/json"})
    public Response deleteDrone(@PathParam("idDrone") int idDrone){
        SmartCity.getInstance().deleteDrone(idDrone);
        return Response.ok().build();
    }
}

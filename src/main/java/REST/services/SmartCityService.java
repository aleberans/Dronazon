package REST.services;

import REST.beans.Drone;
import REST.beans.SmartCity;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.util.List;


@Path("smartcity")
public class SmartCityService {


    @GET
    @Produces({"application/json"})
    public Response getSmartCityInformation(){
        return Response.ok(SmartCity.getInstance()).build();
    }

    //restituisce la lista di Droni della smartCity
    @Path("listaDroni")
    @GET
    @Produces({"application/json"})
    public Response getListaDroni(){
        return Response.status(200).entity(SmartCity.getInstance().stampaSmartCity()).build();
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

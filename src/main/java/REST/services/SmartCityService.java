package REST.services;

import REST.beans.Drone;
import REST.beans.SmartCity;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("smartcity")
public class SmartCityService {

    //restituisce la lista di Droni nella smartCity
    @GET
    @Produces({"application/json"})
    public Response getSmartCityInformation(){
        return Response.ok(SmartCity.getInstance()).build();
    }

    @Path("add")
    @POST
    @Consumes({"application/json"})
    public Response addDrone(Drone drone){
        SmartCity.getInstance().addDrone(drone);
        String result = "Drone aggiunto in posizione di partenza: "+ "(" + drone.getPosizionePartenza().getxPosizioneIniziale() + ","
                + drone.getPosizionePartenza().getyPosizioneIniziale() +  ")\n" +
                "Attualmente i droni presenti nella smartCity sono: \n" + SmartCity.getInstance().stampaSmartCity();
        return Response.status(201).entity(result).build();
    }

    @DELETE
    @Consumes({"application/json"})
    public Response deleteDrone(Drone drone){
        SmartCity.getInstance().deleteDrone(drone);
        return Response.ok().build();
    }
}

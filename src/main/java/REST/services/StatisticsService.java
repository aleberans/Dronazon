package REST.services;

import REST.beans.SmartCity;
import REST.beans.Statistic;
import REST.beans.Statistics;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

@Path("smartcity/statistics")
public class StatisticsService {

    @GET
    @Produces({"application/json"})
    public Response getStatistics(){return Response.ok(Statistics.getInstance()).build();}

    @Path("add")
    @POST
    @Consumes({"application/json"})
    public Response addStatistic(Statistic stat){
        Statistics.getInstance().addStatistic(stat);
        String response = "Statistica aggiunta!";
        return Response.status(201).entity(response).build();
    }

    @GET
    @Path("/queryconsegne")
    public Response getMediaNumeroConsegneDroniBetweenTimestamps(@QueryParam("from") String timestamp1, @QueryParam("to") String timestamp2){
        return Response.ok(Statistics.getInstance().getMediaNumeroConsegneBetweenTimestamp(timestamp1, timestamp2)).build();
    }

    @GET
    @Path("/querykm")
    public Response getMediaKmPercorsiDroniBetweenTimestamps(@QueryParam("from") String timestamp1, @QueryParam("to") String timmestamp2){
        return Response.ok(Statistics.getInstance().getMediaKMPercorsiBetweenTimestamp(timestamp1, timmestamp2)).build();
    }

}

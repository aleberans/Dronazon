package REST.services;

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
        return Response.ok().build();
    }

    /*@GET
    @Path("/query")
    public Response getMediaNumeroConsegneDroniBetweenTimestamps(@QueryParam("from") long timestamp1, @QueryParam("to") long timestamp2){

    }*/

}

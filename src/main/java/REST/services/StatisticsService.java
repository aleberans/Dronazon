package REST.services;

import REST.beans.SmartCity;
import REST.beans.Statistic;
import REST.beans.Statistics;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

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
        List<Statistic> list = Statistics.getInstance().getList();
        list =list.stream().filter(stat ->Timestamp.valueOf(stat.getTimestamp()).after(Timestamp.valueOf(timestamp1))
                                                && Timestamp.valueOf(stat.getTimestamp()).before(Timestamp.valueOf(timestamp2)))
                        .collect(Collectors.toList());
        double mean = list.stream().map(Statistic::getNumeroConsegne).reduce(0.0, Double::sum) / list.size();
        return Response.status(200).entity(mean).build();
    }

    @GET
    @Path("/querykm")
    public Response getMediaKmPercorsiDroniBetweenTimestamps(@QueryParam("from") String timestamp1, @QueryParam("to") String timestamp2){
        List<Statistic> list = Statistics.getInstance().getList();
        list =list.stream().filter(stat ->Timestamp.valueOf(stat.getTimestamp()).after(Timestamp.valueOf(timestamp1))
                                                && Timestamp.valueOf(stat.getTimestamp()).before(Timestamp.valueOf(timestamp2)))
                        .collect(Collectors.toList());
        double mean = list.stream().map(Statistic::getKmPercorsi).reduce(0.0, Double::sum) / list.size();
        return Response.status(200).entity(mean).build();
    }

}

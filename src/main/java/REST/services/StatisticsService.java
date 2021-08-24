package REST.services;

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

    @Path("ultimeNStatistiche")
    @GET
    @Produces({"application/json"})
    public Response getUltimeNStatistiche(@QueryParam("from") String from){
        int n = Integer.parseInt(from);
        List<Statistic> statistics = Statistics.getInstance().getStatistics();

        statistics = statistics.stream()
                .skip(statistics.size() - n).collect(Collectors.toList());

        return Response.status(200).entity(Statistics.stampStatistics(statistics))
                .build();
    }

    @Path("/mediaKmPercorsiConsegne")
    @GET
    @Produces({"application/json"})
    public Response getMediaKmPercorsiDroniBetweenTimestamps(@QueryParam("timestamp1") String timestamp1, @QueryParam("timestamp2") String timestamp2){
        List<Statistic> list = Statistics.getInstance().getStatistics();
        list = list.stream().filter(stat -> (Timestamp.valueOf(stat.getTimestamp()).after(Timestamp.valueOf(timestamp1))
                || Timestamp.valueOf(stat.getTimestamp()).equals(Timestamp.valueOf(timestamp1)))
                                                && (Timestamp.valueOf(stat.getTimestamp()).before(Timestamp.valueOf(timestamp2))
                || Timestamp.valueOf(stat.getTimestamp()).equals(Timestamp.valueOf(timestamp2))))
                        .collect(Collectors.toList());
        double mean = list.stream().map(Statistic::getKmPercorsi).reduce(0.0, Double::sum) / list.size();
        return Response.status(200).entity(mean).build();
    }

    @Path("/mediaNumeroConsegne")
    @Produces({"application/json"})
    @GET
    public Response getMediaConsegne(@QueryParam("timestamp1") String timestamp1, @QueryParam("timestamp2") String timestamp2){
        List<Statistic> list = Statistics.getInstance().getStatistics();
        list = list.stream().filter(statistic ->
                (Timestamp.valueOf(statistic.getTimestamp()).after(Timestamp.valueOf(timestamp1))
                || Timestamp.valueOf(statistic.getTimestamp()).equals(Timestamp.valueOf(timestamp1)))
                                                && (Timestamp.valueOf(statistic.getTimestamp()).before(Timestamp.valueOf(timestamp2))
                || Timestamp.valueOf(statistic.getTimestamp()).equals(Timestamp.valueOf(timestamp2))))
                        .collect(Collectors.toList());
        double mean = list.stream().map(Statistic::getNumeroConsegne).reduce(0.0, Double::sum) / list.size();
        return Response.status(200).entity(mean).build();
    }

}

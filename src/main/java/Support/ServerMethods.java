package Support;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class ServerMethods {

    public static String sendStatistics(List<Drone> drones){
        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        double mediaCountConsegne = 0;
        double mediaKmPercorsi = 0.0;
        double mediaInquinamento = 0;
        double mediaBatteriaResidua = 0;
        int countDroniAttivi = 0;

        mediaInquinamento = drones.stream().map(
                drone -> drone.getBufferPM10().stream().reduce(0.0, Double::sum)
                    / drone.getBufferPM10().size()).reduce(0.0, Double::sum);
        mediaCountConsegne = drones.stream().map(Drone::getCountConsegne).reduce(0, Integer::sum);
        mediaBatteriaResidua = drones.stream().map(Drone::getBatteria).reduce(0, Integer::sum);
        mediaKmPercorsi = drones.stream().map(Drone::getKmPercorsiSingoloDrone).reduce(0.0, Double::sum);
        countDroniAttivi = (int) drones.stream().map(Drone::getId).count();

        Statistic statistic = new Statistic(ts.toString(),  mediaCountConsegne/countDroniAttivi,
                mediaKmPercorsi / countDroniAttivi,
                mediaInquinamento / countDroniAttivi,
                mediaBatteriaResidua/countDroniAttivi);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        return "Output from Server .... \n" + response.getEntity(String.class);
    }

    public static List<Drone> addDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }

    public static void removeDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/remove/" + drone.getId());

        ClientResponse response = webResource.type("application/json").delete(ClientResponse.class, drone.getId());

        if (response.getStatus() != 200){
            throw new RuntimeException("Fallito : codice HTTP " + response.getStatus());
        }
    }
}

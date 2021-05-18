package DronazonPackage;

import REST.beans.Drone;
import REST.beans.SmartCity;
import REST.beans.Statistic;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import network.Network;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DroneClient {

    private final static Random rnd = new Random();
    private int batteria;
    private int id;
    private int portaAscoltoComunicazioneDroni;
    private String indirizzoIP;

    public DroneClient(int id, int portaAscoltoComunicazioneDroni, String indirizzoIP){
        this.id = id;
        this.portaAscoltoComunicazioneDroni = portaAscoltoComunicazioneDroni;
        this.indirizzoIP = indirizzoIP;
    }

    public static void main(String[] args) {

        try{
            BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

            List<Drone> drones = addDroneServer();
            System.out.println(drones);

            while(!bf.readLine().equals("quit")){

            }
            System.out.println("Il drone è uscito dalla rete in maniera forzata!");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String sendStatistics(){
        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        int numeroConsegne = 10;
        int kmPercorsi = 2;
        int inquinamento = 9;
        int batteriaResidua = 3;

        Statistic statistic = new Statistic(ts.toString(), numeroConsegne, kmPercorsi, inquinamento, batteriaResidua);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        return "Output from Server .... \n" + response.getEntity(String.class);
    }

    public static List<Drone> addDroneServer(){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        String id = Integer.toString(rnd.nextInt(10000));
        String portaAscolto = Integer.toString(rnd.nextInt(2000-1000) + 1000);

        Drone drone = new Drone(id, portaAscolto, "localhost");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }
}

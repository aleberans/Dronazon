package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DroneClient<isMaster> {

    private final static Random rnd = new Random();
    private int id;
    private int portaAscoltoComunicazioneDroni;
    private String indirizzoIP;
    private DroneClient nextDrone;
    private int batteria = 100;
    private boolean static isMaster;

    public DroneClient(int id, int portaAscoltoComunicazioneDroni, String indirizzoIP){
        this.id = id;
        this.portaAscoltoComunicazioneDroni = portaAscoltoComunicazioneDroni;
        this.indirizzoIP = indirizzoIP;
    }

    public static void main(String[] args) {

        try{
            BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

            List<Drone> drones = addDroneServer();
            System.out.println(drones.size());

            if (drones.size() == 1) {
                //this.isMaster = true;
                drones.get(0).setNextDrone(drones.get(0));
            }
            else{
                //ultimo punta al primo
                drones.get(drones.size()-1).setNextDrone(drones.get(0));
                //penultimo punta all'ultimo
                drones.get(drones.size()-2).setNextDrone(drones.get(drones.size()-1));
            }

            System.out.println(drones.get(drones.size()-1).getId());
            System.out.println(drones.get(drones.size()-1).getNextDrone());

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
        String ip = "localhost";

        MultivaluedMap<String, String> droneParams = new MultivaluedMapImpl();

        droneParams.add("id", id);
        droneParams.add("porta", portaAscolto);
        droneParams.add("ip", ip);
        ClientResponse response = webResource.type(MediaType.APPLICATION_FORM_URLENCODED).post(ClientResponse.class, droneParams);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }
}

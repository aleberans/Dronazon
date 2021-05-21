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
import org.eclipse.paho.client.mqttv3.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DroneClient{

    private final static Random rnd = new Random();
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

            String id = Integer.toString(rnd.nextInt(10000));
            String portaAscolto = Integer.toString(rnd.nextInt(1000) + 1000);
            String ip = "localhost";

            Drone drone = new Drone(id, portaAscolto, ip);

            List<Drone> drones = addDroneServer(drone);


            if (drones.size()==1)
                drone.setIsMaster(true);

            ringUpdateNextDrone(drone, drones);

            if (drone.getIsMaster())
                subTopic("dronazon/smartcity/orders/");


            /*while(!bf.readLine().equals("quit")){


            }*/
            System.out.println("Il drone è uscito dalla rete in maniera forzata!");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void subTopic(String topic) {
        MqttClient client;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        int qos = 0;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            client.connect();

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connectionlost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {

                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            System.out.println(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            client.subscribe(topic,qos);
            System.out.println(clientId + " Subscribed to topics : " + topic);

        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    private static void ringUpdateNextDrone(Drone drone, List<Drone> drones) {
        drone.setNextDrone(drones.get(0));

        for(Drone d: drones){
            if (d.getNextDrone() == drones.get(0))
                d.setNextDrone(drone);
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

    public static List<Drone> addDroneServer(Drone drone){
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        clientConfig.getClasses().add(JacksonJsonProvider.class);
        Client client = Client.create(clientConfig);

        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

        return response.getEntity(new GenericType<List<Drone>>() {});
    }

}

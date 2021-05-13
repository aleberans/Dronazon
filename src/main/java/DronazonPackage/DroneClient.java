package DronazonPackage;

import REST.beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

public class DroneClient {

    private static Random rnd = new Random();
    private boolean isMaster = false;
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
            System.out.println(addDroneServer());

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String addDroneServer(){
        Client client = Client.create();
        WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

        String id = Integer.toString(rnd.nextInt(10000));
        String portaAscolto = "9999"; //sistemare

        Drone drone = new Drone(id, portaAscolto, "localhost");

        ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);
        System.out.println("Output from Server .... \n");
        return  response.getEntity(String.class);
    }
}

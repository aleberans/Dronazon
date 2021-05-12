package DronazonPackage;

import REST.beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

public class DroneClient {

    private boolean isMaster = false;
    private int id;
    private int portaAscoltoComunicazioneDroni;
    private String indirizzo;

    public DroneClient(int id, int portaAscoltoComunicazioneDroni, String indirizzo){
        this.id = id;
        this.portaAscoltoComunicazioneDroni = portaAscoltoComunicazioneDroni;
        this.indirizzo = indirizzo;
    }

    public static void main(String[] args) {

        try{
            Client client = Client.create();
            WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

            Random rnd = new Random();
            String id = Integer.toString(rnd.nextInt(10000));
            String portaAscolto = "9999"; //sistemare


            Drone drone = new Drone(id, portaAscolto, "to do");


            ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

            System.out.println("Output from Server .... \n");
            String output = response.getEntity(String.class);
            System.out.println(output);

        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}

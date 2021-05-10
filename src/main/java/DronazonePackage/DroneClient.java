package DronazonePackage;

import REST.beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

public class DroneClient {

    public static void main(String[] args) {

        try{
            Client client = Client.create();
            WebResource webResource = client.resource("http://localhost:1337/smartcity/add");

            Random rnd = new Random(10);
            String id = Integer.toString(rnd.nextInt(10000));
            String portaAscolto = "9999"; //sistemare
            Drone drone = new Drone(id, portaAscolto, "http://localhost:1337/");


            ClientResponse response = webResource.type("application/json").post(ClientResponse.class, drone);

            System.out.println("Output from Server .... \n");
            String output = response.getEntity(String.class);
            System.out.println(output);

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}

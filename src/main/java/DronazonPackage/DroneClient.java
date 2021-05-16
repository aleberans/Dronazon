package DronazonPackage;

import REST.beans.Drone;
import REST.beans.Statistic;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;
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
            BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));

            System.out.println(addDroneServer());
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

import REST.beans.Drone;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Amministratore {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("Seleziona cosa vuoi fare:\n" +
                "1 - stampa l'elenco dei droni nella rete\n" +
                "2 - ultime n statistiche globali relative alla smart city\n" +
                "3 - media del numero di consegne effettuate dai droni della smart-city tra due timestamp t1 e t2\n" +
                "4 - media dei chilometri percorsi dai droni della smart-city tra due time-stamp t1 e t2\n" +
                "attendo....");
        String input = sc.nextLine();

        String output;
        switch (input){
            case "1":
                output = getListaDroni();
                break;
            default:
                output = "Invalid insert";
                break;
        }
        System.out.println(output);
    }

    public static String getListaDroni(){
        Client client = Client.create();

        WebResource webResource = client
                .resource("http://localhost:1337/smartcity");

        ClientResponse response = webResource.accept("application/json")
                .get(ClientResponse.class);

        String output = response.getEntity(String.class);

        String stamp = "Output from Server .... n)" + output;

        return stamp;
    }

}

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Scanner;

public class Amministratore {

    private static ClientResponse response;

    public static void main(String[] args){
        String decisione = "Seleziona cosa vuoi fare:\n" +
                "1 - stampa l'elenco dei droni nella rete\n" +
                "2 - ultime n statistiche globali relative alla smart city\n" +
                "3 - media del numero di consegne effettuate dai droni della smart-city tra due timestamp t1 e t2\n" +
                "4 - media dei chilometri percorsi dai droni della smart-city tra due timestamp t1 e t2\n" +
                "attendo....";
        Scanner sc = new Scanner(System.in);
        MultivaluedMap<String, String> parametri = new MultivaluedMapImpl();
        Client client = Client.create();
        String url = "http://localhost:1337/smartcity";
        System.out.println(decisione);
        String scelta = sc.nextLine();

        while(!scelta.equals("quit")) {

            switch (scelta) {
                case "1":
                    WebResource webResource = client.resource(url + "/listaDroni");
                    response = webResource.accept("application/json").get(ClientResponse.class);

                    checkResponse();

                    System.out.println("Output from Server... \n" + response.getEntity(String.class)  + "\n\n");
                    System.out.println(decisione);
                    break;
                case "2":
                    System.out.println("Quante statistiche globali vuoi visualizzare?");
                    parametri.clear();
                    parametri.add("from", sc.nextLine());

                    webResource = client.resource(url + "/statistics/ultimeNStatistiche").queryParams(parametri);
                    response = webResource.accept("application/json").get(ClientResponse.class);

                    checkResponse();

                    System.out.println("Output from server... \n\n" + response.getEntity(String.class)  + "\n" );
                    System.out.println(decisione);
                    break;
                case "3":
                    System.out.println("Inserisci due timestamp per fare la richiesta:\nPrimo timestamp: ");
                    parametri.add("timestamp1", sc.nextLine());
                    System.out.println("Secondo timestamp: ");
                    parametri.add("timestamp2", sc.nextLine());

                    webResource = client.resource(url + "/statistics/mediaNumeroConsegne").queryParams(parametri);
                    response = webResource.accept("application/json").get(ClientResponse.class);

                    checkResponse();

                    System.out.print("Media consegne: " + response.getEntity(String.class) + "\n\n");
                    System.out.println(decisione);
                    break;
                case "4":
                    System.out.println("Inserisci due timestamp per fare la richiesta:\nPrimo timestamp: ");
                    parametri.add("timestamp1", sc.nextLine());
                    System.out.println("Secondo timestamp: ");
                    parametri.add("timestamp2", sc.nextLine());

                    webResource = client.resource(url + "/statistics/mediaKmPercorsiConsegne").queryParams(parametri);
                    response = webResource.accept("application/json").get(ClientResponse.class);

                    checkResponse();

                    System.out.println("Media km percorsi consegne: " + response.getEntity(String.class)  + "\n\n");
                    System.out.println(decisione);
                    break;
                default:
                    System.out.println("Invalid insert, riprova...\n");
                    System.out.println(decisione);
            }
            scelta = sc.nextLine();
        }
    }

    private static void checkResponse() {
        if (response.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
        }
    }
}

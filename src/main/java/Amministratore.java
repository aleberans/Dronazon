import REST.beans.Drone;
import REST.beans.SmartCity;
import REST.beans.Statistics;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Scanner;

public class Amministratore {

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        while(true) {
            System.out.println("Seleziona cosa vuoi fare:\n" +
                    "1 - stampa l'elenco dei droni nella rete\n" +
                    "2 - ultime n statistiche globali relative alla smart city\n" +
                    "3 - media del numero di consegne effettuate dai droni della smart-city tra due timestamp t1 e t2\n" +
                    "4 - media dei chilometri percorsi dai droni della smart-city tra due time-stamp t1 e t2\n" +
                    "attendo....");
            int input = sc.nextInt();

            String output;
            switch (input) {
                case 1:  output = getListaDroni();
                         break;
                case 2:
                    System.out.println("Quante statistiche globali vuoi visualizzare?");
                    int n = sc.nextInt();
                    output = getNGlobalStatistics(n);
                         break;
                case 3: output = "to do";
                        break;
                default: output = "Invalid insert";
                         break;
            }
            System.out.println(output);
        }
    }

    private static String getMediaNumeroConsegneBetweenTimestamp(Timestamp timestamp1, Timestamp timestamp2) throws IOException {
        Client client = Client.create();
        WebResource webResource = client.resource("http://localhost:1337/smartcity/statistics/");
        ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

        String output = response.getEntity(String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Statistics statistics = objectMapper.readValue(output, Statistics.class);
        return  "Output from Server .... \n" + statistics.getMediaNumeroConsegneBetweenTimestamp(timestamp1, timestamp2);
    }

    private static String getNGlobalStatistics(int n) throws IOException {
        Client client = Client.create();
        WebResource webResource = client.resource("http://localhost:1337/smartcity/statistics/");
        ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

        String output = response.getEntity(String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Statistics statistics = objectMapper.readValue(output, Statistics.class);
        return  "Output from Server .... \n" + statistics.stampStatistics(n);

    }

    public static String getListaDroni() throws IOException {
        Client client = Client.create();
        WebResource webResource = client.resource("http://localhost:1337/smartcity");
        ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

        String output = response.getEntity(String.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SmartCity smartCity = objectMapper.readValue(output, SmartCity.class);

        return  "Output from Server .... \n" + smartCity.stampaSmartCity();


    }



}

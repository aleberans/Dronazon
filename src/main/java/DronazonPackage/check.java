package DronazonPackage;

import REST.beans.Statistic;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class check {
    public static void main(String[] args) throws IOException {

        Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());
        //SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Statistic statistic = new Statistic(ts.toString(), 5, 2, 9, 3);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        System.out.println("Output from Server .... \n");
        System.out.println(response.getEntity(String.class));

        /*String timestamp1 = "2021-05-14 22:40:46.5";
        Timestamp t1 = Timestamp.valueOf(timestamp1);
        Timestamp t2 = Timestamp.valueOf(timestamp1);

        if (t1.equals(t2))
            System.out.println("ok");
        else
            System.out.println("no");*/

    }
}

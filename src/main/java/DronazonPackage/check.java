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

        /*Client client = Client.create();
        WebResource webResource2 = client.resource("http://localhost:1337/smartcity/statistics/add");

        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());
        //SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Statistic statistic = new Statistic(ts.toString(), 10, 2, 9, 3);
        ClientResponse response = webResource2.type("application/json").post(ClientResponse.class, statistic);
        System.out.println("Output from Server .... \n");
        System.out.println(response.getEntity(String.class));


        /*String ts = "2021-05-14 10:59:24.033";
        Date date = new Date();
        Timestamp t2 = new Timestamp(date.getTime());
        Timestamp t1 = Timestamp.valueOf(ts);
        if (t2.after(t1))
            System.out.println("ok");
        else
            System.out.println("no");*/

        Scanner sc = new Scanner(System.in);
        int i = sc.nextInt();
        sc.nextLine();
        double d = sc.nextDouble();
        sc.nextLine();
        String s = sc.nextLine();

        System.out.println("String: " + s);
        System.out.println("Double: " + d);
        System.out.println("Int: " + i);
    }
}

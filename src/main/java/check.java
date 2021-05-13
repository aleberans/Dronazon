import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class check {
    public static void main(String[] args) throws IOException {

        SimpleDateFormat sdf3 = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();
        Timestamp ts = new Timestamp(date.getTime());

        System.out.println(sdf3.format(ts));
    }
}

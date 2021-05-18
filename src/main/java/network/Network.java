package network;

import DronazonPackage.DroneClient;

import java.util.ArrayList;
import java.util.List;

public class Network {

    private static List<DroneClient> rete;

    public static void main(String[] args) {

        rete = new ArrayList<>();
        System.out.println(rete.get(0));
    }

    public void addDroneClient(DroneClient droneClient){
        rete.add(droneClient);
    }

}

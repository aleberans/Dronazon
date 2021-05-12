package DronazonPackage;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;


import static java.lang.Thread.sleep;

public class Dronazon{

    public static void main(String[] args) {

        MqttClient client;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        String topic = "dronazon/smartcity/orders/";
        int qos = 2;
        Ordine ordine;


        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            System.out.println(clientId + " Connecting Broker " + broker);
            client.connect(connOpts);
            System.out.println(clientId + " Connected");

            while(true) {
                ordine = new Ordine();
                MqttMessage message = new MqttMessage(ordine.toString().getBytes());

                // Set the QoS on the Message
                message.setQos(qos);
                System.out.println(clientId + " Publishing message: " + ordine + " ...");
                client.publish(topic, message);
                System.out.println(clientId + " Message published");
                sleep(5000);
            }

            /*if (client.isConnected())
                client.disconnect();
            System.out.println("Publisher " + clientId + " disconnected");*/

        } catch (MqttException me) {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}

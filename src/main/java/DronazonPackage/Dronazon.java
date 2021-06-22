package DronazonPackage;

import com.google.gson.Gson;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import org.eclipse.paho.client.mqttv3.*;


import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class Dronazon{

    private static final Logger LOGGER = Logger.getLogger(DroneClient.class.getSimpleName());

    public static void main(String[] args) {

        MqttClient client;
        String broker = "tcp://localhost:1883";
        String clientId = MqttClient.generateClientId();
        String topic = "dronazon/smartcity/orders/";
        int qos = 0;
        Ordine ordine;

        try {
            client = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            // Connect the client
            LOGGER.info(clientId + " Connecting Broker " + broker);
            client.connect(connOpts);
            LOGGER.info(clientId + " Connected - Thread PID: " + Thread.currentThread().getId());

            // Callback
            client.setCallback(new MqttCallback() {
                public void messageArrived(String topic, MqttMessage message) {
                    // Not used Here
                }

                public void connectionLost(Throwable cause) {
                    LOGGER.info(clientId + " Connection lost! cause:" + cause.getMessage());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Until the delivery is completed, messages with QoS 1 or 2 are retained from the client
                    // Delivery for a message is completed when all acknowledgments have been received
                    // When the callback returns from deliveryComplete to the main thread, the client removes the retained messages with QoS 1 or 2.
                    if (token.isComplete()) {
                        LOGGER.info(clientId + " Message delivered - Thread PID: " + Thread.currentThread().getId());
                    }
                }
            });

            while(true){
                ordine = new Ordine();
                Gson gjson = new Gson();
                String payload = gjson.toJson(ordine);
                MqttMessage message = new MqttMessage(payload.getBytes());

                // Set the QoS on the Message
                message.setQos(qos);
                LOGGER.info(clientId + " Publishing message: " + message + " ...");
                client.publish(topic, message);
                //System.out.println(clientId + " Message published - Thread PID: " + Thread.currentThread().getId());
                Thread.sleep(5000);
            }


            /*if (client.isConnected())
                client.disconnect();
            System.out.println("Publisher " + clientId + " disconnected - Thread PID: " + Thread.currentThread().getId());*/

        } catch (MqttException me) {
            LOGGER.info("reason " + me.getReasonCode());
            LOGGER.info("msg " + me.getMessage());
            LOGGER.info("loc " + me.getLocalizedMessage());
            LOGGER.info("cause " + me.getCause());
            LOGGER.info("excep " + me);
            me.printStackTrace();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}

package DronazonPackage;

import org.eclipse.paho.client.mqttv3.*;


import static java.lang.Thread.sleep;

public class Dronazon{

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
            System.out.println(clientId + " Connecting Broker " + broker);
            client.connect(connOpts);
            System.out.println(clientId + " Connected - Thread PID: " + Thread.currentThread().getId());

            // Callback
            client.setCallback(new MqttCallback() {
                public void messageArrived(String topic, MqttMessage message) {
                    // Not used Here
                }

                public void connectionLost(Throwable cause) {
                    System.out.println(clientId + " Connection lost! cause:" + cause.getMessage());
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Until the delivery is completed, messages with QoS 1 or 2 are retained from the client
                    // Delivery for a message is completed when all acknowledgments have been received
                    // When the callback returns from deliveryComplete to the main thread, the client removes the retained messages with QoS 1 or 2.
                    if (token.isComplete()) {
                        System.out.println(clientId + " Message delivered - Thread PID: " + Thread.currentThread().getId());
                    }
                }
            });

            while(true){
                ordine = new Ordine();
                MqttMessage message = new MqttMessage(ordine.toString().getBytes());

                // Set the QoS on the Message
                message.setQos(qos);
                System.out.println(clientId + " Publishing message: " + message + " ...");
                client.publish(topic, message);
                //System.out.println(clientId + " Message published - Thread PID: " + Thread.currentThread().getId());
                Thread.sleep(5000);
            }


            /*if (client.isConnected())
                client.disconnect();
            System.out.println("Publisher " + clientId + " disconnected - Thread PID: " + Thread.currentThread().getId());*/

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

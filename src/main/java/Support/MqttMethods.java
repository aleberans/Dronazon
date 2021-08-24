package Support;

import DronazonPackage.Ordine;
import DronazonPackage.QueueOrdini;
import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;

import java.util.logging.Logger;

public class MqttMethods {

    private static final Logger LOGGER = Logger.getLogger(MqttMethods.class.getSimpleName());
    private static final Gson gson = new Gson();

    public static void subTopic(String topic, MqttClient client, QueueOrdini queueOrdini) {
        try {
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);
            connectOptions.setKeepAliveInterval(60);
            int qos = 0;
            client.connect();

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    //LOGGER.info(clientId + " Connection lost! cause:" + cause.getMessage()+ "-  Thread PID: " + Thread.currentThread().getId());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    //String time = new Timestamp(System.currentTimeMillis()).toString();
                    String receivedMessage = new String(message.getPayload());
                    /*LOGGER.info(clientId +" Received a Message! - Callback - Thread PID: " + Thread.currentThread().getId() +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + receivedMessage +
                            "\n\tQoS:     " + message.getQos() + "\n");*/

                    Ordine ordine = gson.fromJson(receivedMessage, Ordine.class);
                    queueOrdini.add(ordine);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });
            //LOGGER.info(clientId + " Subscribing ... - Thread PID: " + Thread.currentThread().getId());
            client.subscribe(topic,qos);
            //LOGGER.info(clientId + " Subscribed to topics : " + topic);

        } catch (MqttException me) {
            LOGGER.info("reason " + me.getReasonCode());
            LOGGER.info("msg " + me.getMessage());
            LOGGER.info("loc " + me.getLocalizedMessage());
            LOGGER.info("cause " + me.getCause());
            LOGGER.info("excep " + me);
            me.printStackTrace();

        }

    }
}

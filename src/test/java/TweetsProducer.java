import com.github.jcustenborder.kafka.connect.twitter.HashtagEntity;
import com.github.jcustenborder.kafka.connect.twitter.Status;
import com.github.jcustenborder.kafka.connect.twitter.User;
import de.fhdortmund.core.Starter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

public class TweetsProducer {

    private static User getMockUser() {
        User.Builder user = User.newBuilder();
        user.setName("Testttt");
        user.setWithheldInCountries(new ArrayList<>());
        return user.build();
    }

    private static Status getMockStatus() {
        Status.Builder status = Status.newBuilder();
        status.setCreatedAt(new DateTime());
        status.setCurrentUserRetweetId(0L);
        status.setFavoriteCount(100);
        status.setFavorited(false);
        status.setWithheldInCountries(new ArrayList<>());
        status.setContributors(new ArrayList<>());

        List<HashtagEntity> hashtags = new ArrayList<>();
        hashtags.add(new HashtagEntity("pro7", 0, 0));

        if (Math.random() > 0.5) {
            hashtags.add(new HashtagEntity("jklive", 0, 1));
        }

        status.setHashtagEntities(hashtags);
        status.setId(123465L);
        status.setText("MOCK");
        status.setUser(getMockUser());
        return status.build();
    }

    public static void main(String[] args) throws Exception {
        Properties envProps = Starter.loadEnvProperties("configuration/dev.properties");
        Properties producerProps = Starter.buildStreamsProperties(envProps, "mocker");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer");
        producerProps.put(ProducerConfig.ACKS_CONFIG, "0");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, "0");
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, "1");
        producerProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");

        Producer<String, Status> producer = new KafkaProducer<>(producerProps);
        Random random = new Random();

        while (true) {
            try {
                producer.send(new ProducerRecord<>(envProps.getProperty("tweets.topic.name"), random.nextLong()+"", getMockStatus()));
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

package de.fhdortmund.core;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.specific.SpecificData;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Starter {
    /**
     * Lädt die Umgebungseinstellungen
     *
     * @param fileName Dateiname
     * @return Props
     */
    public static Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();

        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        // Add Static Props
        InputStream is = Starter.class.getResourceAsStream("/twitter.properties");
        envProps.load(is);
        is.close();

        return envProps;
    }

    /**
     * Baut die Kafka Einstellungen aus den App Einstellungen
     *
     * @param envProps App Einstellungen
     * @return Kafka Einstellungen
     */
    public static Properties buildStreamsProperties(Properties envProps, String className) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.CLIENT_ID_CONFIG, "service-" + className + "-" + Math.random());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        SpecificData.get().addLogicalTypeConversion(new TimeConversions.TimestampConversion());

        return props;
    }

    /**
     * Erstellt die benötigten Topics im Kafka System
     *
     * @param topics   Benötigte Topics
     * @param envProps App Einstellungen
     */
    private static void createTopics(String[] topics, Properties envProps) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        AdminClient client = AdminClient.create(config);

        List<NewTopic> kafkaTopics = new ArrayList<>();

        for (String topic : topics) {
            kafkaTopics.add(new NewTopic(
                    topic,
                    1,
                    (short) 1
            ));
        }

        client.createTopics(kafkaTopics);
        client.close();
    }

    /**
     * Fügt die Workers hinzu und erhebt die benötigten Topics
     *
     * @param envProps App Einstellungen
     * @return Typologie
     */
    public static Topology buildTopology(IStreamWorker worker, Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        worker.buildTopology(builder, envProps);

        createTopics(worker.getRequiredTopics(envProps), envProps);

        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        System.out.println("Run Module: " + System.getenv("MODULE"));
        Class<?> workerClass = Class.forName(System.getenv("MODULE"));
        Object instance = workerClass.newInstance();

        if (!(instance instanceof IStreamWorker)) {
            throw new Exception("Given class isn't a IStreamWorker");
        }

        IStreamWorker streamWorker = (IStreamWorker) instance;

        /*try {
            System.out.println("Warte 10 Sekunden");
            Thread.sleep(10000);
            System.out.println("Beginne Start...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        Properties envProps = loadEnvProperties(args[0]);
        Properties streamProps = buildStreamsProperties(envProps, System.getenv("MODULE"));
        Topology topology = buildTopology(streamWorker, envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                if (streamWorker instanceof IDisposable) {
                    ((IDisposable) streamWorker).dispose();
                }
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

}
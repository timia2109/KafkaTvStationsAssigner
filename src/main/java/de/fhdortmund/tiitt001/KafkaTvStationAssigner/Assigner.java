package de.fhdortmund.tiitt001.KafkaTvStationAssigner;

import com.github.jcustenborder.kafka.connect.twitter.HashtagEntity;
import com.github.jcustenborder.kafka.connect.twitter.Status;
import de.fhdortmund.core.IStreamWorker;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nimmt Tweets entgegen,
 * prüft diese gegen die definierten Aliase
 * und schiebt die weiter in einen anderen Stream
 */
public class Assigner implements IStreamWorker {

    /** Serienhashtag => Senderhashtag */
    private ConcurrentHashMap<String, String> mappedHashtags;

    public KeyValue<String, TvStationTweet> handleTweet(String _key, Status status) {
        HashtagEntity detectedHashtag = null;
        List<HashtagEntity> hashtags = status.getHashtagEntities();

        if (hashtags != null) {
            for (HashtagEntity hashtag : hashtags) {
                if (mappedHashtags.containsKey(hashtag.getText())) {
                    detectedHashtag = hashtag;
                    break;
                }
            }
        }

        if (detectedHashtag != null) {
            // Relevanten Tweet gefunden! :)
            TvStationTweet message = new TvStationTweet();
            message.setContent(status.getText());
            message.setCreatedAt(status.getCreatedAt());
            message.setTvStation(mappedHashtags.get(detectedHashtag.getText()));

            return new KeyValue<>("", message);
        }

        return null;
    }

    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {

        ConfigTools configTools = new ConfigTools(envProps);

        mappedHashtags = new ConcurrentHashMap<>(configTools.getDefaultAliases());
        String outputTopicName = envProps.getProperty("output.topic.name");

        KStream<String, Status> rawMovies = streamsBuilder
                .stream(envProps.getProperty("input.topic.name"));

        rawMovies.map(this::handleTweet)
                .to(outputTopicName, Produced.with(Serdes.String(), moduleSerdes(envProps)));
    }

    private SpecificAvroSerde<TvStationTweet> moduleSerdes(Properties envProps) {
        SpecificAvroSerde<TvStationTweet> avroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));

        avroSerde.configure(serdeConfig, false);
        return avroSerde;
    }
}

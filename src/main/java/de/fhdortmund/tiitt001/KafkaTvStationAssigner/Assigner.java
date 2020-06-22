package de.fhdortmund.tiitt001.KafkaTvStationAssigner;

import com.github.jcustenborder.kafka.connect.twitter.HashtagEntity;
import com.github.jcustenborder.kafka.connect.twitter.Status;
import de.fhdortmund.core.IStreamWorker;
import de.fhdortmund.olhem002.TvStationAliases.TvStationAlias;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nimmt Tweets entgegen,
 * prüft diese gegen die definierten Aliase
 * und schiebt die weiter in einen anderen Stream
 */
public class Assigner implements IStreamWorker, Transformer<String, Status, KeyValue<Long, TvStationTweet>> {

    /**
     * Serienhashtag => Senderhashtag
     */
    private ConcurrentHashMap<String, String> mappedHashtags;

    /**
     * Handler für Aliase, welche von einem anderen Modul erkannt werden
     *
     * @param key   Key des Aliases
     * @param alias Wertinformationen zum Alias
     */
    public void handleAlias(String key, TvStationAlias alias) {
        if (alias.getIsValid()) {
            mappedHashtags.put(key, alias.getTvStation());
        } else {
            mappedHashtags.remove(key);
        }
    }

    /**
     * Transformiert einen Tweet
     *
     * @param key   Immer null
     * @param value Tweet
     * @return
     */
    @Override
    public KeyValue<Long, TvStationTweet> transform(String key, Status value) {
        HashtagEntity detectedHashtag = null;
        List<HashtagEntity> hashtags = value.getHashtagEntities();

        if (hashtags != null) {
            for (HashtagEntity hashtag : hashtags) {
                if (mappedHashtags.containsKey(hashtag.getText())) {
                    detectedHashtag = hashtag;
                    break;
                }
            }
        }

        String tweetLanguage = value.getLang();
        // Sprachfilter (damit nur deutsche Tweets erkannt werden)
        if (tweetLanguage != null && !tweetLanguage.contains("de") && !tweetLanguage.contains("und")) {
            return null;
        }

        if (detectedHashtag != null) {
            // Relevanten Tweet gefunden! :)
            TvStationTweet message = new TvStationTweet();
            message.setContent(value.getText());
            message.setCreatedAt(value.getCreatedAt());
            message.setTvStation(mappedHashtags.get(detectedHashtag.getText()));
            message.setUsername(value.getUser().getScreenName());

            return new KeyValue<>(value.getId(), message);
        }

        return null;
    }

    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {
        ConfigTools configTools = new ConfigTools(envProps);

        mappedHashtags = new ConcurrentHashMap<>(configTools.getDefaultAliases());
        String outputTopicName = envProps.getProperty("tvstations.topic.name");

        KStream<String, Status> tweetsStream = streamsBuilder.stream(envProps.getProperty("tweets.topic.name"));
        tweetsStream.transform(() -> this).to(outputTopicName, Produced.with(Serdes.Long(), moduleSerdes(envProps)));

        KStream<String, TvStationAlias> aliasesStream = streamsBuilder.stream(envProps.getProperty("tvstationaliases.topic.name"));
        aliasesStream.foreach(this::handleAlias);
    }

    @Override
    public String[] getRequiredTopics(Properties envProps) {
        return new String[]{
                envProps.getProperty("tweets.topic.name"),
                envProps.getProperty("tvstations.topic.name"),
                envProps.getProperty("tvstationaliases.topic.name")
        };
    }

    public static SpecificAvroSerde<TvStationTweet> moduleSerdes(Properties envProps) {
        SpecificAvroSerde<TvStationTweet> avroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));

        avroSerde.configure(serdeConfig, false);
        return avroSerde;
    }

    @Override
    public void init(ProcessorContext context) {
    }

    @Override
    public void close() {
    }
}

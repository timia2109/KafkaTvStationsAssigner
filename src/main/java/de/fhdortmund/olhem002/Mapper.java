package de.fhdortmund.olhem002;

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
import de.fhdortmund.tiitt001.*;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class Assigner implements IStreamWorker, Transformer<String, Status, KeyValue<String, TvStationTweet>> {

    private ConcurrentHashMap<String, String> hashes;
    private ConcurrentHashMap<String, String> mappedHashtags;

    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {
        ConfigTools configTools = new ConfigTools(envProps);

        mappedHashtags = new ConcurrentHashMap<>(configTools.getDefaultAliases());
        String outputTopicName = envProps.getProperty("tvstations.topic.name");

        KStream<String, Status> tweetsStream = streamsBuilder.stream(envProps.getProperty("tweets.topic.name"));
        tweetsStream.transform(() -> this).to(outputTopicName, Produced.with(Serdes.String(), moduleSerdes(envProps)));

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

    @Override
    public KeyValue<String, TvStationAlias> transform(String key, Status value) {
        List<HashtagEntity> hashtags = value.getHashtagEntities();
        for (HashtagEntity hashtag : hashtags) {
            if (mappedHashtags.containsKey(hashtag.getText())) {
                if (hashes != null) {
                    for (HashtagEntity hash : hashtags) {
                        if (mappedHashtags.containsKey(hashtag.getText())) {
                        } else {
                            if (hashes.containsKey(hashtag.getText())) {
                                mappedHashtags.put(hash.getText(), hashtag.getText());
                                hashes.remove(hashtag.getText());
                                return new KeyValue<>(hash.getText(), new TvStationAlias(hashtag.getText(), true));
                            } else {
                                hashes.put(hash.getText(), hashtag.getText());
                                return null;
                            }
                        }
                    }
                } else {
                    hashes = new ConcurrentHashMap<>();
                    for (HashtagEntity hash : hashtags) {
                        if (mappedHashtags.containsKey(hashtag.getText())) {
                        } else {
                            hashes.put(hash.getText(), hashtag.getText());
                        }
                    }
                    return null;
                }
            }
        }
    }
}
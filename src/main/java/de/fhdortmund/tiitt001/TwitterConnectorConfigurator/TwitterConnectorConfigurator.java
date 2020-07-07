package de.fhdortmund.tiitt001.TwitterConnectorConfigurator;

import de.fhdortmund.core.IStreamWorker;
import de.fhdortmund.core.Starter;
import de.fhdortmund.olhem002.TvStationAliases.TvStationAlias;
import de.fhdortmund.tiitt001.KafkaTvStationAssigner.Assigner;
import de.fhdortmund.tiitt001.KafkaTvStationAssigner.ConfigTools;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class TwitterConnectorConfigurator implements IStreamWorker {

    private Set<String> usedHashtags;
    private Configurator configurator;

    private synchronized void handleReconfiguration(String key, TvStationAlias alias) {
        if (alias.getIsValid()) {
            usedHashtags.add(key);
        }
        else {
            usedHashtags.remove(key);
        }

        configurator.refresh(new HashSet<>(usedHashtags));
    }

    @Override
    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {
        ConfigTools configTools = new ConfigTools(envProps);
        Map<String, String> aliases = configTools.getDefaultAliases();

        usedHashtags = aliases.keySet();

        configurator = new Configurator(envProps);
        configurator.init(usedHashtags);

        KStream<String, TvStationAlias> aliasesStream = streamsBuilder.stream(envProps.getProperty("tvstationaliases.topic.name"), Consumed.with(
                Serdes.String(),
                Starter.moduleSerdes(envProps)
        ));
        aliasesStream.foreach(this::handleReconfiguration);
    }

    @Override
    public String[] getRequiredTopics(Properties envProps) {
        return new String[] {
                envProps.getProperty("tvstationaliases.topic.name")
        };
    }
}

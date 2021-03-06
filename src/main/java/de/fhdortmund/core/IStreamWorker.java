package de.fhdortmund.core;

import org.apache.kafka.streams.StreamsBuilder;

import java.util.Properties;

/**
 * Handler welcher sich um Topics kümmert
 */
public interface IStreamWorker {

    /**
     * Fügt die benötigten Streams zu dem StreamsBuilder hinzu
     * @param streamsBuilder StreamsBuilder
     * @param envProps Anwendungseinstellungen
     */
    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps);

    /**
     * Gibt die von dieser Instanz benötigten Topics zurück
     * @param envProps Anwendungseinstellungen
     * @return Liste mit Topics
     */
    public String[] getRequiredTopics(Properties envProps);
}

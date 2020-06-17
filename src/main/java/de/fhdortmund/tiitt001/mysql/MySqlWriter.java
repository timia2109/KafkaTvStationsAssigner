package de.fhdortmund.tiitt001.mysql;

import de.fhdortmund.core.IDisposable;
import de.fhdortmund.core.IStreamWorker;
import de.fhdortmund.tiitt001.KafkaTvStationAssigner.Assigner;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.joda.time.DateTime;

import java.sql.*;
import java.util.Properties;

public class MySqlWriter implements IStreamWorker, IDisposable {

    private Connection connection;

    public void handleTvStationAssignment(String key, TvStationTweet tvStationTweet) {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO Entry (tv_station, createdAt, content, username) VALUES (?,?,?,?)");
            ps.setString(1, tvStationTweet.getTvStation());
            ps.setTimestamp(2, new Timestamp(tvStationTweet.getCreatedAt().getMillis()));
            ps.setString(3, tvStationTweet.getContent());
            ps.setString(4, tvStationTweet.getUsername());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Einfügen in Datenbank");
            e.printStackTrace();
        }
    }

    @Override
    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {
        KStream<String, TvStationTweet> tweetsStream = streamsBuilder.stream(envProps.getProperty("tvstations.topic.name"));
        tweetsStream.foreach(this::handleTvStationAssignment);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();

            connection = DriverManager.getConnection(envProps.getProperty("mysql.connection"));
        } catch (SQLException ex) {
            // handle any errors
            System.err.println("SQLException: " + ex.getMessage());
            System.err.println("SQLState: " + ex.getSQLState());
            System.err.println("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String[] getRequiredTopics(Properties envProps) {
        return new String[]{
                envProps.getProperty("tvstations.topic.name")
        };
    }

    @Override
    public void dispose() {
        try {
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

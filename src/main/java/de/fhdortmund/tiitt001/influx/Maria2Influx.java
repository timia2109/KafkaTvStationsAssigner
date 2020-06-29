package de.fhdortmund.tiitt001.influx;

import de.fhdortmund.core.Starter;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class Maria2Influx {

    public static void main(String... args) throws Exception {
        Properties envProps = Starter.loadEnvProperties("configuration/dev.properties");

        InfluxDBWriter writer = new InfluxDBWriter();
        writer.envProps = envProps;
        writer.connect();

        Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        Connection connection = DriverManager.getConnection(envProps.getProperty("mysql.connection"));
        connection.setAutoCommit(true);

        Statement statement = connection.createStatement();
        statement.execute("SELECT * FROM Entry");
        ResultSet resultSet = statement.getResultSet();
        while (resultSet.next()) {
            TvStationTweet tvs = new TvStationTweet(
                    resultSet.getString("tv_station"),
                    resultSet.getString("content"),
                    resultSet.getString("username"),
                    new DateTime(resultSet.getDate("createdAt").getTime())
            );
            writer.handleTvStationAssignment(0L, tvs);
        }
    }

}

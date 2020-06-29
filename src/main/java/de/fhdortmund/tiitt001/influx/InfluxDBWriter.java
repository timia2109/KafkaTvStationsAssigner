package de.fhdortmund.tiitt001.influx;

import de.fhdortmund.core.IDisposable;
import de.fhdortmund.core.IStreamWorker;
import de.fhdortmund.tiitt001.KafkaTvStationAssigner.Assigner;
import de.fhdortmund.tiitt001.KafkaTvStationsAssigner.TvStationTweet;
import okhttp3.OkHttpClient;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class InfluxDBWriter implements IStreamWorker, IDisposable {

    private InfluxDB influxDB;
    public Properties envProps;

    public void connect() {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder()
                .connectTimeout(40, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        influxDB = InfluxDBFactory.connect(envProps.getProperty("influx.host"), envProps.getProperty("influx.user"), envProps.getProperty("influx.pass"), okHttpClientBuilder);
        influxDB.setDatabase(envProps.getProperty("influx.db"));
    }

    public void handleTvStationAssignment(Long key, TvStationTweet tvStationTweet) {
        influxDB.write(Point.measurement(envProps.getProperty("tvstations.topic.name"))
                .time(tvStationTweet.getCreatedAt().getMillis(), TimeUnit.MILLISECONDS)
                .tag("tvStation", tvStationTweet.getTvStation())
                .addField("username", tvStationTweet.getUsername())
                .addField("content", tvStationTweet.getContent())
                .build()
        );
    }

    @Override
    public void buildTopology(StreamsBuilder streamsBuilder, Properties envProps) {
        KStream<Long, TvStationTweet> tweetsStream = streamsBuilder.stream(envProps.getProperty("tvstations.topic.name"), Consumed.with(
                Serdes.Long(),
                Assigner.moduleSerdes(envProps)
        ));
        tweetsStream.foreach(this::handleTvStationAssignment);
        this.envProps = envProps;

        try {
            connect();
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
            influxDB.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

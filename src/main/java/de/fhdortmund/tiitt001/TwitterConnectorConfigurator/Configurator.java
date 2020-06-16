package de.fhdortmund.tiitt001.TwitterConnectorConfigurator;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;

import java.util.Properties;
import java.util.Set;

public class Configurator {

    private Properties envProps;

    public Configurator(Properties envProps) {
        this.envProps = envProps;
    }

    public JSONObject getConfiguration(Set<String> hashtags) {
        String hashtagsJoined = String.join(", ", hashtags);

        return new JSONObject()
                .put("connector.class", "com.github.jcustenborder.kafka.connect.twitter.TwitterSourceConnector")
                .put("tasks.max", "1")
                .put("topics", envProps.getProperty("tweets.topic.name"))
                .put("process.deletes", false)
                .put("kafka.status.topic", envProps.getProperty("tweets.topic.name"))
                .put("kafka.delete.topic", "")
                .put("filter.keywords", hashtagsJoined)
                .put("twitter.oauth.accessToken", envProps.getProperty("twitter.oauth.accessToken"))
                .put("twitter.oauth.accessTokenSecret", envProps.getProperty("twitter.oauth.accessTokenSecret"))
                .put("twitter.oauth.consumerSecret", envProps.getProperty("twitter.oauth.consumerSecret"))
                .put("twitter.oauth.consumerKey", envProps.getProperty("twitter.oauth.consumerKey"));
    }

    public void init(Set<String> hashtags) {
        // Prüfen ob der Connector schon definiert wurde
        HttpResponse<String> resp = Unirest.get(envProps.getProperty("connect.instance")+"/connectors/"+envProps.getProperty("twitter.connector.name")+"/status")
                .asString();

        if (resp.isSuccess()) {
            refresh(hashtags);
        }
        else {
            JSONObject body = new JSONObject()
                    .put("config", getConfiguration(hashtags))
                    .put("name", envProps.getProperty("twitter.connector.name"));

            Unirest.post(envProps.getProperty("connect.instance") + "/connectors")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .asString();
        }
    }

    public void refresh(Set<String> hashtags) {
        Unirest.put(envProps.getProperty("connect.instance")+"/connectors/"+envProps.getProperty("twitter.connector.name")+"/config")
                .header("Content-Type", "application/json")
                .body(getConfiguration(hashtags))
                .asString();
    }

}

package de.fhdortmund.tiitt001.KafkaTvStationAssigner;

import java.util.HashMap;
import java.util.Properties;

public class ConfigTools {
    private Properties envProps;

    public ConfigTools(Properties envProps) {
        this.envProps = envProps;
    }

    private String[] getTvStations() {
        return envProps.getProperty("config.tvStations").split(",");
    }

    public HashMap<String,String> getDefaultAliases() {
        HashMap<String, String> aliases = new HashMap<>();

        // Assigner beachtet nur Hashtags die als Key in der Map vorkommen,
        // daher alle hinzufügen
        for (String station : getTvStations()) {
            aliases.put(station, station);
        }

        // Default Aliases hinzufügen
        String value = envProps.getProperty("config.tvStations.defaultAlias");
        String[] entries = value.split(",");

        for (String entry : entries) {
            String[] pair = entry.split("=");
            aliases.put(pair[0], pair[1]);
        }

        return aliases;
    }
}

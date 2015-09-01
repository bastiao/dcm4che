package org.dcm4che3.conf.api.upgrade;

import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;

import java.util.Map;
import java.util.Properties;

public interface UpgradeScript {
    String NO_VERSION = "-NO_VERSION-";

    void upgrade(UpgradeContext upgradeContext) throws ConfigurationException;

    class UpgradeContext {
        private String fromVersion;
        private String toVersion;
        private Properties properties;
        private Map<String,Object> scriptConfig;
        private Configuration configuration;
        private DicomConfiguration dicomConfiguration;
        private UpgradeScriptMetadata upgradeScriptMetadata;

        public UpgradeContext() {
        }

        public UpgradeContext(String fromVersion, String toVersion, Properties properties, Map<String, Object> scriptConfig, Configuration configuration, DicomConfiguration dicomConfiguration) {
            this(fromVersion, toVersion, properties, scriptConfig, configuration, dicomConfiguration, null);
        }

        public UpgradeContext(String fromVersion, String toVersion, Properties properties, Map<String, Object> scriptConfig, Configuration configuration, DicomConfiguration dicomConfiguration, UpgradeScriptMetadata upgradeScriptMetadata) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.properties = properties;
            this.scriptConfig = scriptConfig;
            this.configuration = configuration;
            this.dicomConfiguration = dicomConfiguration;
            this.upgradeScriptMetadata = upgradeScriptMetadata;
        }

        public Object getFromVersion() {
            return fromVersion;
        }

        public Object getToVersion() {
            return toVersion;
        }

        public Configuration getConfiguration() {
            return configuration;
        }

        public DicomConfiguration getDicomConfiguration() {
            return dicomConfiguration;
        }

        public Properties getProperties() {
            return properties;
        }
        
        public Map<String,Object> getScriptConfig() {
            return scriptConfig;
        }

        public UpgradeScriptMetadata getUpgradeScriptMetadata() {
            return upgradeScriptMetadata;
        }
    }

    @ConfigurableClass
    class UpgradeScriptMetadata {

        /**
         * The version of this upgrade script when it was last time executed, taken from @ScriptVersion
         */
        @ConfigurableProperty
        String lastVersionExecuted;

        public String getLastVersionExecuted() {
            return lastVersionExecuted;
        }

        public void setLastVersionExecuted(String lastVersionExecuted) {
            this.lastVersionExecuted = lastVersionExecuted;
        }
    }
}

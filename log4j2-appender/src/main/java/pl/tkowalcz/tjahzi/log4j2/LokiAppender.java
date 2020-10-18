package pl.tkowalcz.tjahzi.log4j2;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import pl.tkowalcz.tjahzi.LoggingSystem;
import pl.tkowalcz.tjahzi.TjahziInitializer;
import pl.tkowalcz.tjahzi.http.ClientConfiguration;
import pl.tkowalcz.tjahzi.http.HttpClientFactory;
import pl.tkowalcz.tjahzi.http.NettyHttpClient;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Loki Appender.
 */
@Plugin(name = "Loki", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class LokiAppender extends AbstractAppender {

    /**
     * Builds LokiAppender instances.
     *
     * @param <B> The type to build
     */
    public static class Builder<B extends LokiAppender.Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<LokiAppender> {

        public static final int BYTES_IN_MEGABYTE = 1024 * 1024;

        @PluginBuilderAttribute
        @Required(message = "No Loki address provided for LokiAppender")
        private String host;

        @PluginBuilderAttribute
        @Required(message = "No Loki port provided for LokiAppender")
        private int port;

        @PluginBuilderAttribute
        private int connectTimeoutMillis = 0;

        @PluginBuilderAttribute
        private int readTimeoutMillis = 0;

        @PluginBuilderAttribute
        private int maxRetries = 0;

        @PluginBuilderAttribute
        private int bufferSizeMegabytes = 32;

        @PluginBuilderAttribute
        private boolean useOffHeapBuffer = true;

        @PluginElement("Headers")
        private Header[] headers;

        @PluginElement("Labels")
        private Label[] labels;

        @Override
        public LokiAppender build() {
            ClientConfiguration configurationBuilder = ClientConfiguration.builder()
                    .withHost(host)
                    .withConnectionTimeoutMillis(connectTimeoutMillis)
                    .withPort(port)
                    .withMaxRetries(maxRetries)
                    .withRequestTimeoutMillis(readTimeoutMillis)
                    .build();

            NettyHttpClient httpClient = HttpClientFactory
                    .defaultFactory()
                    .getHttpClient(configurationBuilder);

            LoggingSystem loggingSystem = new TjahziInitializer().createLoggingSystem(
                    httpClient,
                    getBufferSizeMegabytes() * BYTES_IN_MEGABYTE,
                    isUseOffHeapBuffer()
            );

            HashMap<String, String> lokiLabels = Maps.newHashMap();
            Arrays.stream(labels).forEach(property -> {
                        String value = getConfiguration()
                                .getStrSubstitutor()
                                .replace(property.getValue());

                        lokiLabels.put(
                                property.getName(),
                                value
                        );
                    }
            );

            return new LokiAppender(
                    getName(),
                    getLayout(),
                    getFilter(),
                    isIgnoreExceptions(),
                    getPropertyArray(),
                    lokiLabels,
                    loggingSystem
            );
        }

        public String getHost() {
            return host;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public Header[] getHeaders() {
            return headers;
        }

        public B setHost(String host) {
            this.host = host;
            return asBuilder();
        }

        public B setConnectTimeoutMillis(final int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return asBuilder();
        }

        public B setReadTimeoutMillis(final int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return asBuilder();
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getBufferSizeMegabytes() {
            return bufferSizeMegabytes;
        }

        public void setBufferSizeMegabytes(int bufferSizeMegabytes) {
            this.bufferSizeMegabytes = bufferSizeMegabytes;
        }

        public boolean isUseOffHeapBuffer() {
            return useOffHeapBuffer;
        }

        public void setUseOffHeapBuffer(boolean useOffHeapBuffer) {
            this.useOffHeapBuffer = useOffHeapBuffer;
        }

        public void setHeaders(Header[] headers) {
            this.headers = headers;
        }

        public Label[] getLabels() {
            return labels;
        }

        public void setLabels(Label[] labels) {
            this.labels = labels;
        }
    }

    /**
     * @return a builder for a LokiAppender.
     */
    @PluginBuilderFactory
    public static <B extends LokiAppender.Builder<B>> B newBuilder() {
        return new LokiAppender.Builder<B>().asBuilder();
    }

    private final Map<String, String> lokiLabels;
    private final LoggingSystem loggingSystem;

    private LokiAppender(
            String name,
            Layout<? extends Serializable> layout,
            Filter filter,
            boolean ignoreExceptions,
            Property[] properties,
            Map<String, String> lokiLabels,
            LoggingSystem loggingSystem) {
        super(
                name,
                filter,
                layout,
                ignoreExceptions,
                properties
        );
        Objects.requireNonNull(layout, "layout");

        this.lokiLabels = lokiLabels;
        this.loggingSystem = loggingSystem;
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void append(final LogEvent event) {
        byte[] bytes = getLayout().toByteArray(event);
        loggingSystem.createLogger().log(
                event.getTimeMillis(),
                lokiLabels,
                bytes
        );
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        setStopping();

        boolean stopped = super.stop(
                timeout,
                timeUnit,
                false
        );

        loggingSystem.close(
                (int) timeUnit.toMillis(timeout),
                thread -> {
                    error("");
                }
        );

        setStopped();
        return stopped;
    }

    @Override
    public String toString() {
        return "LokiAppender{" +
                "name=" + getName() +
                ", state=" + getState() +
                '}';
    }
}
package org.openhab.automation.jrule.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ITJRule {
    private static final String SKIP_DOCKER = "SKIP_DOCKER";
    private static final Network network = Network.newNetwork();
    private static final List<String> logLines = new ArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(ITJRule.class);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> mqttContainer = new GenericContainer<>("eclipse-mosquitto:2.0")
            .withExposedPorts(1883, 9001)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("docker.mqtt")))
            .withCopyFileToContainer(MountableFile.forClasspathResource("/docker/mosquitto/mosquitto.conf"), "/mosquitto/config/mosquitto.conf")
            .withCopyFileToContainer(MountableFile.forClasspathResource("/docker/mosquitto/default.acl"), "/mosquitto/config/default.conf")
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*mosquitto version.*"))
            .withNetwork(network);

    private static final ToxiproxyContainer toxiproxyContainer = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
            .withNetworkAliases("mqtt")
            .withNetwork(network)
            .dependsOn(mqttContainer);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> openhabContainer = new GenericContainer<>("openhab/openhab:3.3.0-debian")
            .withCopyFileToContainer(MountableFile.forHostPath("/etc/localtime"), "/etc/localtime")
            .withCopyFileToContainer(MountableFile.forHostPath("/etc/timezone"), "/etc/timezone")
            .withClasspathResourceMapping("docker/tmp", "/openhab/conf/automation/jrule/jar", BindMode.READ_WRITE)
            .withCopyToContainer(MountableFile.forClasspathResource("docker/conf"), "/openhab/conf")
            .withCopyFileToContainer(MountableFile.forClasspathResource("docker/log4j2.xml", 777), "/openhab/userdata/etc/log4j2.xml")
            .withCopyFileToContainer(MountableFile.forClasspathResource("docker/users.json", 777), "/openhab/userdata/jsondb/users.json")
            .withCopyFileToContainer(MountableFile.forHostPath("target/dependency/rules-engine.jar", 777), "/openhab/addons/rules-engine.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("target/test-1.0-SNAPSHOT.jar", 777), "/openhab/conf/automation/jrule/rules-jar/my-rules.jar")
            .withExposedPorts(8080)
            .withLogConsumer(outputFrame -> {
                logLines.add(outputFrame.getUtf8String().strip());
                new Slf4jLogConsumer(LoggerFactory.getLogger("docker.openhab")).accept(outputFrame);
            })
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*JRule Engine Initializing done.*"))
            .withNetwork(network)
            .dependsOn(toxiproxyContainer)
            .withPrivilegedMode(true);

    private static FileInputStream fis;
    private static ToxiproxyContainer.ContainerProxy mqttProxy;

    @BeforeAll
    static void initClass() throws IOException {
        if (!Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            openhabContainer.start();
            mqttProxy = toxiproxyContainer.getProxy(mqttContainer, 1883);
        } else {
            fis = new FileInputStream("src/test/resources/docker/userdata/logs/openhab.log");
            long size = fis.getChannel().size();
            fis.skip(size);
        }
        Awaitility.await().with()
                .pollDelay(1, TimeUnit.SECONDS)
                .timeout(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .await("thing online")
                .until(() -> getThingState("mqtt:topic:mqtt:generic"), s -> s.equals("ONLINE"));
    }

    @BeforeEach
    void initTest() throws IOException, InterruptedException {
        mqttProxy.setConnectionCut(false);
        logLines.clear();
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            new File("src/test/resources/docker/userdata/example.txt").delete();
        } else {
            openhabContainer.execInContainer("rm", "/openhab/userdata/example.txt");
        }
        sendCommand("MyTestDisturbanceSwitch", "OFF");
    }

    @Test
    public void testMyRuleTurnSwitch2On() throws IOException {
        sendCommand("MyTestSwitch", "OFF");
        sendCommand("MyTestSwitch", "ON");
        wasRuleExecuted("[MyRuleTurnSwitch2On]");
    }

    @Test
    public void testTestExecutingCommandLine() throws IOException, InterruptedException {
        sendCommand("MySwitchGroup", "ON");
        wasRuleExecuted("[TestExecutingCommandLine]");
        verifyFileExist();
    }

    @Test
    public void testChannelTriggered() throws MqttException {
        publishMqttMessage("number/state", "123");
        wasRuleExecuted("[ChannelTriggered]");
    }

    @Test
    public void testTestPrecondition() throws IOException, InterruptedException {
        sendCommand("MyMessageNotification", "abc");
        wasRuleNotExecuted("[MyTestPreConditionRule1]");

        sendCommand("MyTestDisturbanceSwitch", "ON");
        sendCommand("MyMessageNotification", "abc");
        wasRuleExecuted("[MyTestPreConditionRule1]");
    }

    @Test
    public void testStartTrackingNonOnlineThing() {
        Awaitility.await().with()
                .pollDelay(1, TimeUnit.SECONDS)
                .timeout(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .await("thing online")
                .until(() -> getThingState("mqtt:topic:mqtt:generic"), s -> s.equals("ONLINE"));
        mqttProxy.setConnectionCut(true);
        Awaitility.await().with()
                .pollDelay(1, TimeUnit.SECONDS)
                .timeout(20, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .await("thing offline")
                .until(() -> getThingState("mqtt:topic:mqtt:generic"), s -> s.equals("OFFLINE"));
        wasRuleExecuted("[Log every thing that goes offline]");
    }

    private static void publishMqttMessage(String topic, String message) throws MqttException {
        IMqttClient publisher = new MqttClient(String.format("tcp://%s:%s", getMqttHost(), getMqttPort()), "ITJRule");

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(2);
        publisher.connect(options);

        MqttMessage msg = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        msg.setRetained(true);
        publisher.publish(topic, msg);
    }

    private void verifyFileExist() throws IOException, InterruptedException {
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            Assertions.assertTrue(new File("src/test/resources/docker/userdata/example.txt").exists());
        } else {
            Container.ExecResult execResult = openhabContainer.execInContainer("ls", "/openhab/userdata/example.txt");
            Assertions.assertEquals(0, execResult.getExitCode());
        }
    }

    private void wasRuleExecuted(String ruleLogLine) {
        Awaitility.await().with()
                .timeout(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .await("rule executed")
                .until(() -> logLines, v -> containsLine(ruleLogLine, v));
    }

    private void wasRuleNotExecuted(String ruleLogLine) throws InterruptedException {
        Thread.sleep(1000);
        Assertions.assertTrue(notContainsLine(ruleLogLine, logLines));
    }

    @Test
    public void testMyEventValueTest() throws IOException {
        sendCommand("MyTestSwitch2", "ON");
        wasRuleExecuted("[MyEventValueTest]");
    }

    @Test
    public void testMyNumberRule1() throws IOException {
        sendCommand("MyTestNumber", "14");
        sendCommand("MyTestNumber", "10");
        wasRuleExecuted("[MyNumberRule1]");
    }

    private boolean containsLine(String line, List<String> logLines) {
        return logLines.stream().anyMatch(s -> s.contains(line));
    }

    private boolean notContainsLine(String line, List<String> logLines) {
        return logLines.stream().noneMatch(s -> s.contains(line));
    }

    private void sendCommand(String itemName, String value) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .build()) {
            HttpPost request = new HttpPost(String.format("http://%s:%s/rest/items/" + itemName, getOpenhabHost(), getOpenhabPort()));
            request.setEntity(new StringEntity(value));
            CloseableHttpResponse response = client.execute(request);
            Assertions.assertEquals(2, response.getCode() / 100);
        }
    }

    private Optional<Double> getDoubleState(String itemName) throws IOException, ParseException {
        return Optional.ofNullable(getState(itemName)).map(Double::parseDouble);
    }

    private String getState(String itemName) throws IOException, ParseException {
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .build()) {
            HttpGet request = new HttpGet(String.format("http://%s:%s/rest/items/" + itemName + "/state", getOpenhabHost(), getOpenhabPort()));
            CloseableHttpResponse response = client.execute(request);
            Assertions.assertEquals(2, response.getCode() / 100);
            return Optional.of(EntityUtils.toString(response.getEntity()))
                    .filter(s -> !s.equals("NULL")).orElse(null);
        }
    }

    private static String getThingState(String thing) throws IOException, ParseException {
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .build()) {
            HttpGet request = new HttpGet(String.format("http://%s:%s/rest/things/%s/status", getOpenhabHost(), getOpenhabPort(), URLEncoder.encode(thing, StandardCharsets.UTF_8)));
            byte[] credentials = Base64.encodeBase64(("admin:admin").getBytes(StandardCharsets.UTF_8));
            request.setHeader("Authorization", "Basic " + new String(credentials, StandardCharsets.UTF_8));
            CloseableHttpResponse response = client.execute(request);
            if (2 != response.getCode() / 100) {
                return response.getReasonPhrase();
            }
            JsonElement jsonElement = JsonParser.parseString(EntityUtils.toString(response.getEntity()));
            String status = jsonElement.getAsJsonObject().getAsJsonPrimitive("status").getAsString();
            log.debug("querying status for '{}' -> '{}'", thing, status);
            return status;
        }
    }

    private static int getOpenhabPort() {
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            return 8088;
        } else {
            return openhabContainer.getFirstMappedPort();
        }
    }

    private static String getOpenhabHost() {
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            return "localhost";
        } else {
            return openhabContainer.getHost();
        }
    }

    private static int getMqttPort() {
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            return 1883;
        } else {
            return mqttContainer.getFirstMappedPort();
        }
    }

    private static String getMqttHost() {
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            return "localhost";
        } else {
            return mqttContainer.getHost();
        }
    }
}
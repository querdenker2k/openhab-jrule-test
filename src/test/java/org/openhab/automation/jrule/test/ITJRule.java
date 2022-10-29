package org.openhab.automation.jrule.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ITJRule {
    private static final String SKIP_DOCKER = "SKIP_DOCKER";
    private static final Network network = Network.newNetwork();
    private static final List<String> logLines = new ArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(ITJRule.class);
    private final List<String> receivedMqttMessages = new ArrayList<>();

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
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*JRule Engine Rules Reloaded.*")
                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)))
            .withNetwork(network)
            .dependsOn(toxiproxyContainer)
            .withPrivilegedMode(true);

    private static FileInputStream fis;
    private static ToxiproxyContainer.ContainerProxy mqttProxy;
    private @NotNull IMqttClient mqttClient;

    @BeforeAll
    static void initClass() throws IOException, MqttException {
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
    void initTest() throws IOException, InterruptedException, MqttException {
        mqttProxy.setConnectionCut(false);
        logLines.clear();
        if (Boolean.parseBoolean(System.getProperty(SKIP_DOCKER, "false"))) {
            new File("src/test/resources/docker/userdata/example.txt").delete();
        } else {
            openhabContainer.execInContainer("rm", "/openhab/userdata/example.txt");
        }
        sendCommand("MyTestDisturbanceSwitch", "OFF");
        sendCommand("MyTestSwitch", "OFF");
        sendCommand("MyTestSwitch2", "OFF");

        mqttClient = getMqttClient();
        subscribeMqtt("number/state");
    }

    @AfterAll
    static void testFinished() {
        if (openhabContainer != null && openhabContainer.isRunning()) {
            openhabContainer.copyFileFromContainer("/openhab/conf/automation/jrule/jar/jrule-generated.jar", "lib/jrule-generated.jar");
        }
    }

    private void subscribeMqtt(String topic) throws MqttException {

        mqttClient.subscribe(topic, (s, mqttMessage) ->
                receivedMqttMessages.add(new String(mqttMessage.getPayload(), StandardCharsets.UTF_8)));
    }

    @NotNull
    private static IMqttClient getMqttClient() throws MqttException {
        IMqttClient publisher = new MqttClient(String.format("tcp://%s:%s", getMqttHost(), getMqttPort()), "ITJRule");
        MqttConnectOptions options = getMqttConnectOptions();
        publisher.connect(options);
        return publisher;
    }

    @NotNull
    private static MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(2);
        return options;
    }

    @AfterEach
    void unloadTest() throws MqttException {
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
    }

    @Test
    public void testMyRuleTurnSwitch2On() throws IOException {
        sendCommand("MyTestSwitch", "OFF");
        sendCommand("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MyRuleTurnSwitch2On]");
    }

    @Test
    public void testTestExecutingCommandLine() throws IOException, InterruptedException {
        sendCommand("MySwitchGroup", "ON");
        verifyRuleWasExecuted("[TestExecutingCommandLine]");
        verifyFileExist();
    }

    @Test
    public void testTestAction() throws IOException, InterruptedException {
        sendCommand("MyMqttTrigger", "ON");
        verifyRuleWasExecuted("[TestAction]");
        verifyMqttMessageReceived("1313131");
    }

    private void verifyMqttMessageReceived(String s) {
        Assertions.assertTrue(receivedMqttMessages.contains(s));
    }

    @Test
    public void testChannelTriggered() throws MqttException {
        publishMqttMessage("number/state", "123");
        verifyRuleWasExecuted("[ChannelTriggered]");
    }

    @Test
    public void testTestPrecondition() throws IOException, InterruptedException {
        sendCommand("MyMessageNotification", "abc");
        verifyRuleWasNotExecuted("[MyTestPreConditionRule1]");

        sendCommand("MyTestDisturbanceSwitch", "ON");
        sendCommand("MyMessageNotification", "abc");
        verifyRuleWasExecuted("[MyTestPreConditionRule1]");
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
        verifyRuleWasExecuted("[Log every thing that goes offline]");
    }

    private static void publishMqttMessage(String topic, String message) throws MqttException {
        IMqttClient publisher = getMqttClient();

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

    private void verifyRuleWasExecuted(String ruleLogLine) {
        Awaitility.await().with()
                .timeout(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .await("rule executed")
                .until(() -> logLines, v -> containsLine(ruleLogLine, v));
    }

    private void verifyRuleWasNotExecuted(String ruleLogLine) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // no problem here
        }
        Assertions.assertTrue(notContainsLine(ruleLogLine, logLines));
    }

    @Test
    public void testItemCommandTrigger() throws IOException {
        sendCommand("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[ItemCommandTrigger]");
    }

    @Test
    public void testMyNumberRule1() throws IOException {
        sendCommand("MyTestNumber", "14");
        sendCommand("MyTestNumber", "10");
        verifyRuleWasExecuted("[MyNumberRule1]");
    }

    @Test
    public void testTurnOnFanIfTemperatureIsLow() throws IOException {
        sendCommand("MyTemperatureSensor", "22");
        verifyRuleWasNotExecuted("[turnOnFanIfTemperatureIsLow]");
        sendCommand("MyTemperatureSensor", "21");
        verifyRuleWasNotExecuted("[turnOnFanIfTemperatureIsLow]");
        sendCommand("MyTemperatureSensor", "20");
        verifyRuleWasExecuted("[turnOnFanIfTemperatureIsLow]");
    }

    @Test
    public void testGroupMySwitchesChanged() throws IOException {
        sendCommand("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[groupMySwitchesChanged]");
    }

    @Test
    public void testMemberOfCommandTrigger() throws IOException {
        sendCommand("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MemberOfCommandTrigger]");
        logLines.clear();
        sendCommand("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MemberOfCommandTrigger]");
        logLines.clear();
        sendCommand("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[MemberOfCommandTrigger]");
        logLines.clear();
        sendCommand("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[MemberOfCommandTrigger]");
    }

    @Test
    public void testMemberOfUpdateTrigger() throws IOException {
        postUpdate("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MemberOfUpdateTrigger]");
        logLines.clear();
        postUpdate("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MemberOfUpdateTrigger]");
        logLines.clear();
        postUpdate("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[MemberOfUpdateTrigger]");
        logLines.clear();
        postUpdate("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[MemberOfUpdateTrigger]");
    }

    @Test
    public void testMemberOfChangeTrigger() throws IOException {
        postUpdate("MyTestSwitch", "ON");
        verifyRuleWasExecuted("[MemberOfChangeTrigger]");
        logLines.clear();
        postUpdate("MyTestSwitch2", "ON");
        verifyRuleWasExecuted("[MemberOfChangeTrigger]");
    }

    @Test
    public void testTestCron() {
        verifyRuleWasExecuted("[testCron]");
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
            HttpPost request = new HttpPost(String.format("http://%s:%s/rest/items/%s", getOpenhabHost(), getOpenhabPort(), itemName));
            request.setEntity(new StringEntity(value));
            CloseableHttpResponse response = client.execute(request);
            Assertions.assertEquals(2, response.getCode() / 100);
        }
    }

    private void postUpdate(String itemName, String value) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create()
                .build()) {
            HttpPut request = new HttpPut(String.format("http://%s:%s/rest/items/%s/state", getOpenhabHost(), getOpenhabPort(), itemName));
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

package org.openhab.automation.jrule.rules.user;

import org.openhab.automation.jrule.items.generated.JRuleItems;
import org.openhab.automation.jrule.items.generated._MySwitchGroup;
import org.openhab.automation.jrule.rules.JRule;
import org.openhab.automation.jrule.rules.JRuleName;
import org.openhab.automation.jrule.rules.JRuleWhenItemChange;
import org.openhab.automation.jrule.rules.JRuleWhenItemReceivedCommand;
import org.openhab.automation.jrule.rules.JRuleWhenItemReceivedUpdate;
import org.openhab.automation.jrule.rules.event.JRuleItemEvent;
import org.openhab.automation.jrule.rules.value.JRuleOnOffValue;

import java.util.stream.Collectors;


public class TestRules extends JRule {
//    @JRuleName("MyRuleTurnSwitch2On")
//    @JRuleWhenItemChange(item = _MyTestSwitch.ITEM, to = JRuleSwitchTrigger.ON)
//    @JRuleTag({"item", "change"})
//    public void myRuleTurnSwitch2On(JRuleEvent event) {
//        logInfo("triggered with item change: {}, state: {}, oldState: {}",
//                ((JRuleItemEvent) event).getItemName(), ((JRuleItemEvent) event).getState(), ((JRuleItemEvent) event).getOldState());
//        JRuleItems.MyTestSwitch2.sendCommand(JRuleOnOffValue.ON);
//    }
//
//    @JRuleName("MyEventValueTest")
//    @JRuleWhenItemReceivedCommand(item = _MyTestSwitch2.ITEM)
//    public void myEventValueTest(JRuleEvent event) {
//        logInfo("Got value from event: {}", ((JRuleItemEvent) event).getState().getValue());
//    }
//
//    @JRuleName("MyNumberRule1")
//    @JRuleWhenItemChange(item = _MyTestNumber.ITEM, from = "14", to = "10")
//    @JRuleWhenItemChange(item = _MyTestNumber.ITEM, from = "10", to = "12")
//    public void myOrRuleNumber(JRuleEvent event) {
//        logInfo("Got change number: {}", ((JRuleItemEvent) event).getState().getValue());
//    }
//
//    @JRuleName("TestExecutingCommandLine")
//    @JRuleWhenItemReceivedCommand(item = _MySwitchGroup.ITEM)
//    public synchronized void testExecutingCommandLine(JRuleEvent event) {
//        logInfo("Creating dummy file using CLI");
//        executeCommandLine("touch", "/openhab/userdata/example.txt");
//    }
//
//    @JRuleName("ChannelTriggered")
//    @JRuleWhenChannelTrigger(channel = "mqtt:topic:mqtt:generic:numberTrigger")
//    public synchronized void channelTriggered(JRuleEvent event) {
//        logInfo("Channel triggered with value: {}", ((JRuleChannelEvent) event).getEvent());
//    }
//
//    @JRulePrecondition(item = _MyTestDisturbanceSwitch.ITEM, condition = @JRuleCondition(eq = "ON"))
//    @JRuleName("MyTestPreConditionRule1")
//    @JRuleWhenItemReceivedCommand(item = _MyMessageNotification.ITEM)
//    public void testPrecondition(JRuleEvent event) {
//        String notificationMessage = ((JRuleItemEvent) event).getState().getValue();
//        logInfo("It is ok to send notification: {}", notificationMessage);
////        JRuleItems.MySendNoticationItemMqtt.sendCommand(notificationMessage);
//    }
//
//    @JRuleName("Log every thing that goes offline")
//    @JRuleWhenThingTrigger(to = JRuleThingStatus.OFFLINE)
//    public void startTrackingNonOnlineThing(JRuleEvent event) {
//        String offlineThingUID = ((JRuleThingEvent) event).getThing();
//        String status = ((JRuleThingEvent) event).getStatus();
//        logInfo("thing '{}' goes '{}'", offlineThingUID, status);
//    }
//
//    @JRuleName("SetDayBrightness")
//    @JRuleWhenTimeTrigger(hours=22, minutes=30)
//    public synchronized void setDayBrightness(JRuleEvent event) {
//        logInfo("Setting night brightness to 30%");
//        int dimLevel = 30;
//        JRuleItems.MyDimmerBrightness.sendCommand(dimLevel);
//    }
//
//    @JRuleName("turnOnFanIfTemperatureIsLow")
//    @JRuleWhenItemChange(item = _MyTemperatureSensor.ITEM, condition = @JRuleCondition(lte = 20))
//    public synchronized void turnOnFanIfTemperatureIsLow(JRuleItemEvent event) {
//        logInfo("Starting fan since temperature dropped below 20");
//        JRuleItems.MyHeatingFanSwitch.sendCommand(JRuleOnOffValue.ON);
//    }

//    @JRuleName("groupMySwitchesChanged")
//    @JRuleWhenItemChange(item = _MySwitchGroup.ITEM)
//    public synchronized void groupMySwitchGroupChanged(JRuleItemEvent event) {
//        final boolean groupIsOnline = event.getState().getValueAsOnOffValue() == JRuleOnOffValue.ON;
//        final String memberThatChangedStatus = event.getMemberName();
//        String states = JRuleItems.MySwitchGroup.memberItems().stream()
//                .map(jRuleItem -> jRuleItem.getName() + ": " + jRuleItem.getStateAsString())
//                .collect(Collectors.joining(", "));
//        logInfo("All Member states: {}", states);
//        logInfo("Member that changed the status of the Group of switches: {}", memberThatChangedStatus);
//    }

    @JRuleName("MemberOfCommandTrigger")
    @JRuleWhenItemReceivedCommand(item = _MySwitchGroup.ITEM, memberOf = true)
    public synchronized void memberOfCommandTrigger(JRuleItemEvent event) {
        final String memberThatChangedStatus = event.getMemberName();
        logInfo("Member that changed the status of the Group of switches: {}", memberThatChangedStatus);
    }

    @JRuleName("MemberOfUpdateTrigger")
    @JRuleWhenItemReceivedUpdate(item = _MySwitchGroup.ITEM, memberOf = true)
    public synchronized void memberOfUpdateTrigger(JRuleItemEvent event) {
        final String memberThatChangedStatus = event.getMemberName();
        logInfo("Member that changed the status of the Group of switches: {}", memberThatChangedStatus);
    }

    @JRuleName("MemberOfChangeTrigger")
    @JRuleWhenItemChange(item = _MySwitchGroup.ITEM, memberOf = true)
    public synchronized void memberOfChangeTrigger(JRuleItemEvent event) {
        final String memberThatChangedStatus = event.getMemberName();
        logInfo("Member that changed the status of the Group of switches: {}", memberThatChangedStatus);
    }

//    @JRuleName("testCron")
//    @JRuleWhenCronTrigger(cron = "*/5 * * * * *")
//    public void testCron(JRuleEvent event) {
//        logInfo("CRON: Running cron from string every 5 seconds: {}", event);
//    }

//    @JRuleName("TestAction")
//    @JRuleWhenItemChange(item = _MyMqttTrigger.ITEM)
//    public void testAction(JRuleEvent event) {
//        JRuleActions.mqttBrokerMqtt.publishMQTT("number/state", "1313131");
//        logInfo("some info after this");
//    }
}

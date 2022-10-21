package org.openhab.automation.jrule.rules.user;

import org.openhab.automation.jrule.items.JRuleSwitchItem;
import org.openhab.automation.jrule.items.generated.JRuleItems;
import org.openhab.automation.jrule.items.generated._MyMessageNotification;
import org.openhab.automation.jrule.items.generated._MySwitchGroup;
import org.openhab.automation.jrule.items.generated._MyTestDisturbanceSwitch;
import org.openhab.automation.jrule.items.generated._MyTestNumber;
import org.openhab.automation.jrule.items.generated._MyTestSwitch;
import org.openhab.automation.jrule.items.generated._MyTestSwitch2;
import org.openhab.automation.jrule.rules.JRule;
import org.openhab.automation.jrule.rules.JRuleCondition;
import org.openhab.automation.jrule.rules.JRuleName;
import org.openhab.automation.jrule.rules.JRulePrecondition;
import org.openhab.automation.jrule.rules.JRuleTag;
import org.openhab.automation.jrule.rules.JRuleWhenChannelTrigger;
import org.openhab.automation.jrule.rules.JRuleWhenItemChange;
import org.openhab.automation.jrule.rules.JRuleWhenItemReceivedCommand;
import org.openhab.automation.jrule.rules.JRuleWhenThingTrigger;
import org.openhab.automation.jrule.rules.event.JRuleChannelEvent;
import org.openhab.automation.jrule.rules.event.JRuleEvent;
import org.openhab.automation.jrule.rules.event.JRuleItemEvent;
import org.openhab.automation.jrule.rules.event.JRuleThingEvent;
import org.openhab.automation.jrule.rules.value.JRuleOnOffValue;
import org.openhab.automation.jrule.things.JRuleThingStatus;

public class TestRules extends JRule {
    @JRuleName("MyRuleTurnSwitch2On")
    @JRuleWhenItemChange(item = _MyTestSwitch.ITEM, to = JRuleSwitchItem.ON)
    @JRuleTag({"item", "change"})
    public void myRuleTurnSwitch2On(JRuleEvent event) {
        logInfo("triggered with item change: {}, state: {}, oldState: {}",
                ((JRuleItemEvent) event).getItemName(), ((JRuleItemEvent) event).getState(), ((JRuleItemEvent) event).getOldState());
        JRuleItems.MyTestSwitch2.sendCommand(JRuleOnOffValue.ON);
    }

    @JRuleName("MyEventValueTest")
    @JRuleWhenItemReceivedCommand(item = _MyTestSwitch2.ITEM)
    public void myEventValueTest(JRuleEvent event) {
        logInfo("Got value from event: {}", ((JRuleItemEvent) event).getState().getValue());
    }

    @JRuleName("MyNumberRule1")
    @JRuleWhenItemChange(item = _MyTestNumber.ITEM, from = "14", to = "10")
    @JRuleWhenItemChange(item = _MyTestNumber.ITEM, from = "10", to = "12")
    public void myOrRuleNumber(JRuleEvent event) {
        logInfo("Got change number: {}", ((JRuleItemEvent) event).getState().getValue());
    }

    @JRuleName("TestExecutingCommandLine")
    @JRuleWhenItemReceivedCommand(item = _MySwitchGroup.ITEM)
    public synchronized void testExecutingCommandLine(JRuleEvent event) {
        logInfo("Creating dummy file using CLI");
        executeCommandLine("touch", "/openhab/userdata/example.txt");
    }

    @JRuleName("ChannelTriggered")
    @JRuleWhenChannelTrigger(channel = "mqtt:topic:mqtt:generic:numberTrigger")
    public synchronized void channelTriggered(JRuleEvent event) {
        logInfo("Channel triggered with value: {}", ((JRuleChannelEvent) event).getEvent());
    }

    @JRulePrecondition(item = _MyTestDisturbanceSwitch.ITEM, condition = @JRuleCondition(eq = "ON"))
    @JRuleName("MyTestPreConditionRule1")
    @JRuleWhenItemReceivedCommand(item = _MyMessageNotification.ITEM)
    public void testPrecondition(JRuleEvent event) {
        String notificationMessage = ((JRuleItemEvent) event).getState().getValue();
        logInfo("It is ok to send notification: {}", notificationMessage);
//        JRuleItems.MySendNoticationItemMqtt.sendCommand(notificationMessage);
    }

    @JRuleName("Log every thing that goes offline")
    @JRuleWhenThingTrigger(to = JRuleThingStatus.OFFLINE)
    public void startTrackingNonOnlineThing(JRuleEvent event) {
        String offlineThingUID = ((JRuleThingEvent) event).getThing();
        String status = ((JRuleThingEvent) event).getStatus();
        logInfo("thing '{}' goes '{}'", offlineThingUID, status);
    }




//    @JRuleName("trigger with channel")
//    @JRuleTag({"channel"})
//    @JRuleWhenChannelTrigger(channel = "mqtt:topic:mqtt:generic:numberTrigger")
//    public void triggerWithChannel(JRuleEvent event) {
//        logInfo("triggered with channel: {}, event: {}", ((JRuleChannelEvent) event).getChannel(), ((JRuleChannelEvent) event).getEvent());
//        incrementInvocationCounter();
////        var telegram = getAction("telegram", "telegram:telegramBot:telegram");
////        telegram.doAction("sendTelegram", "test");
////        Actions.getTelegramTelegramBotTelegram().sendTelegram("blubba");
//    }

//    @JRuleName("trigger with item command")
//    @JRuleTag({"item", "command"})
//    @JRuleWhenItemReceivedCommand(item = _TestSwitch.ITEM)
//    public void triggerWithItemCommand(JRuleEvent event) {
//        logInfo("triggered with item command: {}, state: {}", ((JRuleItemEvent) event).getItemName(), ((JRuleItemEvent) event).getState());
//        incrementInvocationCounter();
//    }
//
//    @JRuleName("trigger with item update")
//    @JRuleTag({"item", "update"})
//    @JRuleWhenItemReceivedUpdate(item = _TestSwitch.ITEM)
//    public void triggerWithItemUpdate(JRuleEvent event) {
//        logInfo("triggered with item update: {}, state: {}", ((JRuleItemEvent) event).getItemName(), ((JRuleItemEvent) event).getState());
//        incrementInvocationCounter();
//    }
//
//    @JRuleName("trigger with cron")
//    @JRuleTag({"cron"})
//    @JRuleWhenCronTrigger(cron = "*/5 * * * * *")
//    public void triggerWithCron(JRuleEvent event) {
//        logInfo("triggered by cron: {}", ((JRuleTimerEvent) event).toString());
//        incrementInvocationCounter();
//    }
//
//    @JRuleName("trigger with time")
//    @JRuleTag({"time"})
//    @JRuleWhenTimeTrigger(seconds = 5)
//    public void triggerWithTimer(JRuleEvent event) {
//        logInfo("triggered by time: {}", ((JRuleTimerEvent) event).toString());
//        incrementInvocationCounter();
//    }
//
//    private static void incrementInvocationCounter() {
//        JRuleItems.TestCounter.postUpdate(Optional.ofNullable(JRuleItems.TestCounter.getState()).orElse(0D) + 1);
//    }
}

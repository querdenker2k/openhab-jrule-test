package org.openhab.automation.jrule.action.generated;

import org.openhab.core.model.script.actions.Things;
import org.openhab.core.thing.binding.ThingActions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class TelegramAction {
    public static final String SCOPE = "telegram";
    private final ThingActions thingActions;

    public TelegramAction(String thingUid) {
        thingActions = Objects.requireNonNull(Things.getActions(SCOPE, thingUid),
                String.format("action for '%s' with uid '%s' could not be found", SCOPE, thingUid));
    }

    public Object sendTelegram(String message) {
        try {
            Method method = thingActions.getClass().getDeclaredMethod("sendTelegram", String.class);
            return method.invoke(thingActions, message);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("method not found", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("error invoking method", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("cannot access method", e);
        }
    }
}

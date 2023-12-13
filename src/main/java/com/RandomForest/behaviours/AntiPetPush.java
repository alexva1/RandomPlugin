package com.RandomForest.behaviours;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Readonly;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.events.EventHandler;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.managers.BoosterAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.I18nAPI;
import eu.darkbot.api.managers.GameLogAPI.LogMessageEvent;
import eu.darkbot.shared.legacy.LegacyModuleAPI;

@Feature(name = "AntiPetPush", description = "Performs a break after you have been pushed by pet kills.")
public class AntiPetPush implements Behavior, Listener, Configurable<AntiPetPush.Config> {

    private final BotAPI bot;
    private final LegacyModuleAPI legacyModules;
    private final BoosterAPI booster;
    private final I18nAPI i18n;

    private Config config;
    private final Pattern pattern;
    private final Pattern pattern2;
    private Integer counter;
    private boolean found;
    private Integer exp;
    private Integer honor;
    private Double exp_boost;
    private Double honor_boost;
    private boolean isWeakPlayerMessage;

    @Configuration("anti_pet_push.config")
    public static class Config {
        // Talent cruelty that gives up to 25% honor boost
        public @Number(min = 0, max = 25) int CRUELTY = 0;
        // Talent tactics that gives up to 12% exp boost
        public @Number(min = 0, max = 12) int TACTICS = 0;

        // honor and exp modules
        public @Number(min = 0, max = 50) int HONOR_MODULES = 0;
        public @Number(min = 0, max = 50) int EXP_MODULES = 0;

        // push plugin setting for behaviour
        public @Number(min = -1, max = 300) Integer KILL_PAUSE_TIME = -1;
        public @Number(min = 1, max = 10) Integer MAX_KILLS = 5;

        // kill count. (Doesn't work properly)
        @Readonly
        public @Number Integer KILL_COUNT = 0;

        // If true then plugin considers pattern 2
        public boolean CHECK_FOR_WEAK_PLAYER_MESSAGE = false;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    public AntiPetPush(BotAPI bot, LegacyModuleAPI legacyModules, BoosterAPI booster, I18nAPI i18n) {
        // APIs
        this.bot = bot;
        this.legacyModules = legacyModules;
        this.booster = booster;
        this.i18n = i18n;

        // regex patterns
        this.pattern = Pattern.compile(i18n.get("anti_pet_push.honor_exp_pattern"));
        this.pattern2 = Pattern.compile(i18n.get("anti_pet_push.weak_player_pattern"));

        // kill counter
        this.counter = 0;

        // boolean that is true if pattern is detected
        this.found = false;

        // variables that hold exp and honor detected
        this.exp = 0;
        this.honor = 0;

        // running boosters gotten from boosterAPI
        this.exp_boost = 0.0;
        this.honor_boost = 0.0;

        // boolean that is true if pattern 2 is recognized
        this.isWeakPlayerMessage = false;
    }

    // this function is called every time a new log message appears in game
    @EventHandler
    public void onLogReceived(LogMessageEvent ev) throws IOException {
        // This function looks for 2 consecutive detections of pattern 1.
        // Then calculates the ratio of exp/honor to distinguish a npc kill
        // from a ship kill. (npc's ratio is 200 and ship's ratio is 100)

        if (pattern.matcher(ev.getMessage()).matches()) {
            // If pattern 1 is detected, find honor|exp value from string and save it
            if (ev.getMessage().split(" ")[3].equals("honor")) {
                honor = Integer.valueOf(ev.getMessage().split(" ")[2].replace(",", ""));
            } else {
                exp = Integer.valueOf(ev.getMessage().split(" ")[2].replace(",", ""));
            }

            // If found == true then this is the second consecutive recognition of pattern
            // 1.
            // (When an entity is killed, honor and exp rewards goes one after the other)
            if (found) {

                // Get exp and honor booster ammount from boosterAPI. This doesn't include the
                // buffs
                // from modules, talents etc.
                exp_boost = booster.getBoosters().stream().filter(bs -> bs.getName().equals("Experience"))
                        .collect(Collectors.toList()).get(0).getAmount();
                honor_boost = booster.getBoosters().stream().filter(bs -> bs.getName().equals("Honor"))
                        .collect(Collectors.toList()).get(0).getAmount();

                // Calculate the initial values of exp and honor. The categories of buffs are
                // booster buffs, module buffs, talent buffs, formation buffs. Even though the
                // total buff on each category is additive, the overall boost is cumulative.
                exp = (int) (exp * 100 / (100 + exp_boost) * 100 / (100 + config.TACTICS)
                        * 100 / (100 + config.EXP_MODULES));
                honor = (int) (honor * 100 / (100 + honor_boost) * 100 / (100 + config.CRUELTY)
                        * 100 / (100 + config.HONOR_MODULES));

                // If honor == 0 (phoenix gives 0 honor) or ratio is closer to 100 than to 200
                // increade kill counter and reset found boolean.
                if (honor == 0 || exp / honor - 150 < 0) {
                    counter += 1;
                    config.KILL_COUNT = counter;
                    found = false;
                }

                // This is for testing purposes.
                Path path = Paths.get("test.txt");
                Files.write(path,
                        ("Exp booster : " + exp_boost + " , Honor booster : " + honor_boost + " , exp : " + exp
                                + " , honor : " + honor + " , counter : " + counter + " , cruelty : " + config.CRUELTY
                                + " , modules : " + config.HONOR_MODULES + "\n").getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                // This runs when it's the first time that pattern 1 is recognized.
                found = true;
            }
        } else {
            // This runs when log message didn't match with pattern 1
            found = false;
        }

        // If pattern 2 is recognized, set isWeakPlayerMessage boolean to true.
        if (config.CHECK_FOR_WEAK_PLAYER_MESSAGE && pattern2.matcher(ev.getMessage()).matches()) {
            isWeakPlayerMessage = true;
        }

    }

    // This function is called on every tick
    @Override
    public void onTickBehavior() {

        // This function checks if kill counter >= max_kills or if pattern 2
        // is detected and stops for pause_time minutes.
        // Both max_kills and pause_time has been set up by the user.

        if (counter >= config.MAX_KILLS) {
            System.out.println("Pausing bot" +
                    (config.KILL_PAUSE_TIME > 0 ? " for " + config.KILL_PAUSE_TIME + " minutes" : "") +
                    ", you killed more than " + config.MAX_KILLS + " players");

            Long pauseMillis = config.KILL_PAUSE_TIME > 0 ? (long) config.KILL_PAUSE_TIME * 60 * 1000 : null;
            bot.setModule(
                    legacyModules.getDisconnectModule(pauseMillis,
                            i18n.get("anti_pet_push.disconnect.reason.kills", config.MAX_KILLS)));
            counter = 0;
            isWeakPlayerMessage = false;
        }

        if (isWeakPlayerMessage) {
            Long pauseMillis = config.KILL_PAUSE_TIME > 0 ? (long) config.KILL_PAUSE_TIME * 60 * 1000 : null;
            bot.setModule(
                    legacyModules.getDisconnectModule(pauseMillis,
                            i18n.get("anti_pet_push.disconnect.reason.weak_player_message")));
            counter = 0;
            isWeakPlayerMessage = false;
        }
    }
}
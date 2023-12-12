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
import eu.darkbot.api.managers.GameLogAPI.LogMessageEvent;
import eu.darkbot.shared.legacy.LegacyModuleAPI;

@Feature(name = "AntiPetPush", description = "Does a break when you are being pushed by pet")
public class AntiPetPush implements Behavior, Listener, Configurable<AntiPetPush.Config> {

    private final BotAPI bot;
    private final LegacyModuleAPI legacyModules;
    private final BoosterAPI booster;
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
        public @Number(min = 0, max = 25) int CRUELTY = 0;
        public @Number(min = 0, max = 12) int TACTICS = 0;
        public @Number(min = 0, max = 50) int HONOR_MODULES = 0;
        public @Number(min = 0, max = 50) int EXP_MODULES = 0;

        public @Number(min = -1, max = 300) Integer KILL_PAUSE_TIME = -1;
        public @Number(min = 1, max = 10) Integer MAX_KILLS = 5;
        @Readonly
        public @Number Integer KILL_COUNT = 0;
        public boolean CHECK_FOR_WEAK_PLAYER_MESSAGE = false;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    public AntiPetPush(BotAPI bot, LegacyModuleAPI legacyModules, BoosterAPI booster) {
        this.bot = bot;
        this.legacyModules = legacyModules;
        this.pattern = Pattern.compile("You received (\\S+) (honor points|EP).");
        this.pattern2 = Pattern.compile("You get less reward from (\\S+), he is too weak for you!");
        this.counter = 0;
        this.found = false;
        this.exp = 0;
        this.honor = 0;
        this.booster = booster;
        this.exp_boost = 0.0;
        this.honor_boost = 0.0;
        this.isWeakPlayerMessage = false;
    }

    @EventHandler
    public void onLogReceived(LogMessageEvent ev) throws IOException {

        if (pattern.matcher(ev.getMessage()).matches()) {
            if (ev.getMessage().split(" ")[3].equals("honor")) {
                honor = Integer.valueOf(ev.getMessage().split(" ")[2].replace(",", ""));
            } else {
                exp = Integer.valueOf(ev.getMessage().split(" ")[2].replace(",", ""));
            }

            if (found) {
                exp_boost = booster.getBoosters().stream().filter(bs -> bs.getName().equals("Experience"))
                        .collect(Collectors.toList()).get(0).getAmount();
                honor_boost = booster.getBoosters().stream().filter(bs -> bs.getName().equals("Honor"))
                        .collect(Collectors.toList()).get(0).getAmount();

                exp = (int) (exp * 100 / (100 + exp_boost) * 100 / (100 + config.TACTICS)
                        * 100 / (100 + config.EXP_MODULES));
                honor = (int) (honor * 100 / (100 + honor_boost) * 100 / (100 + config.CRUELTY)
                        * 100 / (100 + config.HONOR_MODULES));

                if (honor == 0 || Math.abs((exp / honor) - 100) < 50) {
                    counter += 1;
                    config.KILL_COUNT = counter;
                    found = false;
                }

                Path path = Paths.get("test.txt");
                Files.write(path,
                        ("Exp booster : " + exp_boost + " , Honor booster : " + honor_boost + " , exp : " + exp
                                + " , honor : " + honor + " , counter : " + counter + " , cruelty : " + config.CRUELTY
                                + " , modules : " + config.HONOR_MODULES + "\n").getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                found = true;
            }
        } else {
            found = false;
        }

        if (pattern2.matcher(ev.getMessage()).matches() & config.CHECK_FOR_WEAK_PLAYER_MESSAGE) {
            isWeakPlayerMessage = true;
        }

    }

    @Override
    public void onTickBehavior() {
        if (counter >= config.MAX_KILLS) {
            System.out.println("Pausing bot" +
                    (config.KILL_PAUSE_TIME > 0 ? " for " + config.KILL_PAUSE_TIME + " minutes" : "") +
                    ", you killed more than " + config.MAX_KILLS + " players");

            Long pauseMillis = config.KILL_PAUSE_TIME > 0 ? (long) config.KILL_PAUSE_TIME * 60 * 1000 : null;
            bot.setModule(
                    legacyModules.getDisconnectModule(pauseMillis, "You Killed " + config.MAX_KILLS + " players"));
            counter = 0;
            isWeakPlayerMessage = false;
        }

        if (isWeakPlayerMessage) {
            Long pauseMillis = config.KILL_PAUSE_TIME > 0 ? (long) config.KILL_PAUSE_TIME * 60 * 1000 : null;
            bot.setModule(
                    legacyModules.getDisconnectModule(pauseMillis, "Weak player message detected"));
            counter = 0;
            isWeakPlayerMessage = false;
        }
    }
}
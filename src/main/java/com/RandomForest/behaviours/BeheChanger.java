package com.RandomForest.behaviours;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.types.NpcInfo;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.StarSystemAPI;

@Feature(name = "BeheChanger", description = "changes map when behe is destroyed")
public class BeheChanger implements Behavior, Configurable<BeheChanger.Config> {

    // API
    protected final PluginAPI api;
    protected final BotAPI bot;
    protected final HeroAPI hero;
    protected final StarSystemAPI starSystem;
    protected final PetAPI pet;
    protected final EntitiesAPI entities;
    protected final RepairAPI repair;
    protected final HeroItemsAPI items;

    // Ids of bl maps
    protected final Integer bl_1;
    protected final Integer bl_2;
    protected final Integer bl_3;

    // Working map and npcTable fetched from bot
    protected final ConfigSetting<Integer> workingMap;
    protected final ConfigSetting<Map<String, NpcInfo>> npcTable;

    // Behemoth info found on nptTable
    protected final NpcInfo beheInfo;

    // True if behemoth is on map
    protected boolean isBeheOnMap;
    // Ture if behemoth is near player
    protected boolean isBeheNear;

    // maps that store the order of the change
    protected final Map<Integer, Integer> map;
    protected final Map<Integer, Integer> reversedMap;

    // Variable that stores plugin config settings
    private Config config;

    // Collection of ships around player
    private final Collection<? extends Ship> ships;

    @Configuration("behe_changer.config")
    public static class Config {
        // if true then the order is reversed
        public boolean REVERSED = false;
        // if true then use emp when enemy draws
        public boolean USE_EMP_ON_DRAW = false;
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    public BeheChanger(PluginAPI api, BotAPI bot, HeroAPI hero, StarSystemAPI starSystem, ConfigAPI config,
            PetAPI pet, EntitiesAPI entities, RepairAPI repair, HeroItemsAPI items) {
        this.api = api;
        this.bot = bot;
        this.hero = hero;
        this.starSystem = starSystem;
        this.pet = pet;
        this.entities = entities;
        this.repair = repair;
        this.items = items;

        this.bl_1 = 306;
        this.bl_2 = 307;
        this.bl_3 = 308;
        this.workingMap = config.requireConfig("general.working_map");
        this.npcTable = config.requireConfig("loot.npc_infos");
        this.beheInfo = npcTable.getValue().get("\\\\ Mindfire Behemoth //");
        this.isBeheOnMap = false;
        this.isBeheNear = false;
        this.ships = entities.getShips();

        this.map = new HashMap<Integer, Integer>();
        this.reversedMap = new HashMap<Integer, Integer>();

        map.put(306, 307);
        map.put(307, 308);
        map.put(308, 306);

        reversedMap.put(306, 308);
        reversedMap.put(307, 306);
        reversedMap.put(308, 307);
    }

    @Override
    public void onTickBehavior() {
        pet.setEnabled(true);

        tickDrawFire();
        tickCheckBehe();
    }

    public void tickDrawFire() {
        // This function uses emp if enemies draw. Emp must be in bar.
        if (config.USE_EMP_ON_DRAW) {
            for (Ship ship : ships) {
                if (!ship.getEntityInfo().isEnemy() || !ship.hasEffect(EntityEffect.DRAW_FIRE)
                        || hero.getTarget() != ship)
                    continue;
                items.useItem(SelectableItem.Special.EMP_01);
            }
        }
    }

    public void tickCheckBehe() {
        // This function checks for any disappearance of behemoth. It uses both
        // isBeheNear and isBeheOnMap for a combination of speed and robustness.

        if (!repair.isDestroyed()) {
            boolean beheOnMap = checkBehe();
            boolean beheNear = checkBeheNear();

            if (!isBeheNear && beheNear) {
                isBeheNear = beheNear;
            } else if (isBeheNear && !beheNear) {
                isBeheNear = beheNear;
                rotateMap();
            }
            if (pet.isActive()) {
                if (!isBeheOnMap && beheOnMap) {
                    isBeheOnMap = beheOnMap;
                } else if (isBeheOnMap && !beheOnMap) {
                    isBeheOnMap = beheOnMap;
                    rotateMap();
                }
            }
        }
    }

    public boolean checkBehe() {
        // This function returns true if behemoth is on map. It gets pet's locator npc
        // list from petAPI and checks if behe is included.
        // This way bot can detect if behe is dead even when player is not near.

        return pet.getLocatorNpcs().stream().anyMatch(info -> info == beheInfo);
    }

    public boolean checkBeheNear() {
        // This function returns true if behemoth is near player using EntitiesAPI.
        return entities.getNpcs().stream().map(Npc::getInfo).anyMatch(info -> info == beheInfo);
    }

    public void rotateMap() {
        // Performs a map rotation by changin the working map.
        if (config.REVERSED) {
            workingMap.setValue(reversedMap.get(hero.getMap().getId()));
        } else {
            workingMap.setValue(map.get(hero.getMap().getId()));
        }
    }
}

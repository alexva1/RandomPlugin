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

    protected final PluginAPI api;
    protected final BotAPI bot;
    protected final HeroAPI hero;
    protected final StarSystemAPI starSystem;
    protected final PetAPI pet;
    protected final EntitiesAPI entities;
    protected final RepairAPI repair;
    protected final HeroItemsAPI items;

    protected final Integer bl_1;
    protected final Integer bl_2;
    protected final Integer bl_3;
    protected final ConfigSetting<Integer> workingMap;
    protected final ConfigSetting<Map<String, NpcInfo>> npcTable;
    protected final NpcInfo beheInfo;
    protected boolean isBeheOnMap;
    protected boolean isBeheNear;
    protected final Map<Integer, Integer> map;
    protected final Map<Integer, Integer> reversedMap;
    private Config config;
    private final Collection<? extends Ship> ships;

    @Configuration("behe_changer.config")
    public static class Config {
        public boolean REVERSED = false;
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
        if (!repair.isDestroyed()) {
            boolean beheOnMap = checkBehe();
            boolean beheNear = checkBeheNear();

            if (!isBeheNear & beheNear) {
                isBeheNear = beheNear;
            } else if (isBeheNear & !beheNear) {
                isBeheNear = beheNear;
                rotateMap();
            }
            if(pet.isActive()){
                if (!isBeheOnMap & beheOnMap) {
                    isBeheOnMap = beheOnMap;
                } else if (isBeheOnMap & !beheOnMap) {
                    isBeheOnMap = beheOnMap;
                    rotateMap();
                }
            }
            
        }
    }

    public boolean checkBehe() {
        return pet.getLocatorNpcs().stream().anyMatch(info -> info == beheInfo);
    }

    public boolean checkBeheNear() {
        return entities.getNpcs().stream().map(Npc::getInfo).anyMatch(info -> info == beheInfo);
    }

    public void rotateMap() {
        if (config.REVERSED) {
            workingMap.setValue(reversedMap.get(hero.getMap().getId()));
        } else {
            workingMap.setValue(map.get(hero.getMap().getId()));
        }
    }
}

package top.tobyprime.mcedia;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.player_instance_managers.ArmorStandPlayerManager;

public class Mcedia implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Mcedia.class);


    @Override
    public void onInitialize() {
        new ArmorStandPlayerManager().onInitialize();
    }
}

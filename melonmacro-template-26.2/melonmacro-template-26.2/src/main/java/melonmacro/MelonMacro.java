package melonmacro;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MelonMacro implements ModInitializer {

	public static final String MOD_ID = "melonmacro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Melon Macro loaded!");
	}
}
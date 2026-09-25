package grill24.fishtastic.architectury.neoforge;

import grill24.fishtastic.architectury.IRegistrationApi;

public class RegistrationApiSidedImpl {
    public static IRegistrationApi getInstance() {
        return NeoForgeRegistrationApi.INSTANCE;
    }
}

package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.architectury.IRegistrationApi;

public class RegistrationApiSidedImpl {
    public static IRegistrationApi getInstance() {
        return ForgeRegistrationApi.INSTANCE;
    }
}

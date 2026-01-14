package grill24.fishtastic.architectury.fabric;

import grill24.fishtastic.architectury.IRegistrationApi;

public class RegistrationApiSidedImpl {
    public static IRegistrationApi getInstance() {
        return FabricRegistrationApi.INSTANCE;
    }
}

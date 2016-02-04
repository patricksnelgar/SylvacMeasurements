package com.patrick.Sylvac_Calipers;

import java.util.UUID;

/**
 * Created by Patrick on 29/01/2016.
 */
public abstract interface CommunicationCharacteristics {
    public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_UUID = UUID.fromString("C1B25000-CAAF-6D0E-4C33-7DAE30052840");
    public static final UUID RX_CMD_TO_INSTRUMENT_UUID = UUID.fromString("C1B25012-CAAF-6D0E-4C33-7DAE30052840");
    public static final UUID TX_ANSWER_FROM_INSTRUMENT_UUID = UUID.fromString("C1B25013-CAAF-6D0E-4C33-7DAE30052840");
    public static final UUID TX_RECEIVED_DATA_UUID = UUID.fromString("C1B25010-CAAF-6D0E-4C33-7DAE30052840");

    public static final String ACTION_DATA_AVAILABLE = "Donnees transmises";
    public static final String ACTION_DEVICE_NOT_FOUND = "Instrument non-trouve";
    public static final String ACTION_GATT_CONNECTED = "Connexion reussi";
    public static final String ACTION_GATT_DISCONNECTED = "Deconnexion reussi ou inattendue";
    public static final String ACTION_GATT_SERVICES_DISCOVERED = "Services decouverts";
}

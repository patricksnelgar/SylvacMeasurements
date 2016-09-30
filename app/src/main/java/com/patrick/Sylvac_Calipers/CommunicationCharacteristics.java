package com.patrick.Sylvac_Calipers;

import java.util.UUID;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: interface to hold commonly used keys and UUIDs
 */
public interface CommunicationCharacteristics {
    UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    UUID SERVICE_UUID = UUID.fromString("C1B25000-CAAF-6D0E-4C33-7DAE30052840");
    UUID RX_CMD_TO_INSTRUMENT_UUID = UUID.fromString("C1B25012-CAAF-6D0E-4C33-7DAE30052840");
    UUID TX_ANSWER_FROM_INSTRUMENT_UUID = UUID.fromString("C1B25013-CAAF-6D0E-4C33-7DAE30052840");
    UUID TX_RECEIVED_DATA_UUID = UUID.fromString("C1B25010-CAAF-6D0E-4C33-7DAE30052840");

    String ACTION_DATA_AVAILABLE = "Data received";
    String ACTION_DEVICE_NOT_FOUND = "Device not found";
    String ACTION_GATT_CONNECTED = "Connection successful";
    String ACTION_GATT_DISCONNECTED = "Disconnected";
    String ACTION_GATT_SERVICES_DISCOVERED = "Service discovery complete";

    String DEVICE_ADDRESS = "DEVICE_ADDRESS";
    String EXTRA_DATA = "EXTRA_DATA";
    String NUM_CANAL = "NUM_CANAL";

    String DATA_VALUE = "DATA_VALUE";

    String DEVICE_BONDED = "DEVICE_BONDED";
    String BT_DEVICE = "BT_DEVICE";




}

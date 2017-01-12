package com.patrick.Sylvac_Calipers;

import java.util.UUID;

/**
 * Author: Patrick Snelgar
 * Date: 21/04/2016
 * Description: interface to hold commonly used keys and UUIDs
 */
public interface CommunicationCharacteristics {

    // UUID's for the Sylvac services & characteristics
    UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    UUID SERVICE_UUID = UUID.fromString("C1B25000-CAAF-6D0E-4C33-7DAE30052840");
    UUID RX_CMD_TO_INSTRUMENT_UUID = UUID.fromString("C1B25012-CAAF-6D0E-4C33-7DAE30052840");
    UUID TX_ANSWER_FROM_INSTRUMENT_UUID = UUID.fromString("C1B25013-CAAF-6D0E-4C33-7DAE30052840");
    UUID TX_RECEIVED_DATA_UUID = UUID.fromString("C1B25010-CAAF-6D0E-4C33-7DAE30052840");

    String ACTION_DATA_AVAILABLE = "com.SylvacCalipers.ACTION_DATA_AVAILABLE";
    String ACTION_DEVICE_CONNECTED = "com.SylvacCalipers.ACTION_DEVICE_CONNECTED";
    String ACTION_DEVICE_DISCONNECTED = "com.SylvacCalipers.ACTION_DEVICE_DISCONNECTED";
    String ACTION_DEVICE_SERVICES_DISCOVERED = "com.SylvacCalipers.ACTION_DEVICE_SERVICES_DISCOVERED";

    String DEVICE_ADDRESS = "com.SylvacCalipers.DEVICE_ADDRESS";
    String EXTRA_DATA = "com.SylvacCalipers.EXTRA_DATA";
    String MEASUREMENT_DATA = "com.SylvacCalipers.MEASUREMENT_DATA";
}

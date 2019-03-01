package blufi.espressif;

import blufi.espressif.response.BlufiStatusResponse;

public abstract class BlufiCallback {
    public static final int STATUS_SUCCESS = 0;

    public static final int STATUS_ERR_WRITE = -10;
    public static final int STATUS_ERR_DEV_PBK = -11;
    public static final int STATUS_ERR_ENCRYPT = -12;

    public static final int STATUS_ERR_OP_CODE = -100;

    /**
     * Callback the client close gatt.
     *
     * @param client blufi client
     */
    public void onGattClose(BlufiClient client) {
    }

    /**
     * Callback the client received notification from device
     * @param client blufi client
     * @param pkgType data package type
     * @param subType data subtype
     * @param data notification data
     */
    public void onNotification(BlufiClient client, int pkgType, int subType, byte[] data) {
    }

    /**
     * Callback the client negotiate security with devices
     *
     * @param client blufi client
     * @param status {@link #STATUS_SUCCESS} is successful.
     */
    public void onNegotiateSecurityResult(BlufiClient client, int status) {
    }

    /**
     * Callback the client post configure data result
     *
     * @param client blufi client
     * @param status {@link #STATUS_SUCCESS} means post data successfully.
     */
    public void onConfigureResult(BlufiClient client, int status) {
    }

    /**
     * Callback the device connect the AP
     *
     * @param client blufi client
     * @param response call {@link BlufiStatusResponse#isStaConnected()} to check result
     */
    public void onWifiStateResponse(BlufiClient client, BlufiStatusResponse response) {
    }

    /**
     * Callback the error occur
     *
     * @param client blufi client
     * @param errCode error code
     */
    public void onError(BlufiClient client, int errCode) {
    }
}

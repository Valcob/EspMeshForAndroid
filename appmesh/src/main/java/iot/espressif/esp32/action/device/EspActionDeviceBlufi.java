package iot.espressif.esp32.action.device;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

import iot.espressif.esp32.app.EspApplication;
import iot.espressif.esp32.model.device.ble.MeshBlufiCallback;
import iot.espressif.esp32.model.device.ble.MeshBlufiClient;
import libs.espressif.ble.EspBleUtils;
import libs.espressif.log.EspLog;
import meshblufi.espressif.BlufiCallback;
import meshblufi.espressif.BlufiClient;
import meshblufi.espressif.response.BlufiStatusResponse;

public class EspActionDeviceBlufi implements IEspActionDeviceBlufi {
    private final EspLog mLog = new EspLog(getClass());

    public MeshBlufiClient doActionConnectMeshBLE(@NonNull BluetoothDevice device, int meshVersion,
                                                  @NonNull MeshBlufiCallback userCallback) {
        MeshBlufiClient blufi = new MeshBlufiClient();
        blufi.setMeshVersion(meshVersion);
        Context context = EspApplication.getEspApplication().getApplicationContext();
        BleCallback bleCallback = new BleCallback(blufi, userCallback);
        BluetoothGatt gatt = EspBleUtils.connectGatt(device, context, bleCallback);
        blufi.setBluetoothGatt(gatt);

        return blufi;
    }

    protected static class BleCallback extends BluetoothGattCallback {
        private final EspLog mmLog = new EspLog(getClass());

        private MeshBlufiClient mMeshBlufi;

        private BlufiCallbackImpl mActionCallback;
        private MeshBlufiCallback mUserCallback;

        protected BleCallback(MeshBlufiClient blufi, MeshBlufiCallback userCallback) {
            mMeshBlufi = blufi;
            mActionCallback = new BlufiCallbackImpl();
            mUserCallback = userCallback;
        }

        protected void onBlufiClientSetComplete() {
        }

        protected void onNegotiateSecurityComplete() {
        }

        protected void onReceiveWifiState(BlufiStatusResponse response) {
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            mmLog.d(String.format(Locale.ENGLISH, "onConnectionStateChange status=%d, newState=%d, address=%s",
                    status, newState, gatt.getDevice().getAddress()));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mUserCallback.onGattConnectionChange(gatt, BlufiCallback.STATUS_SUCCESS, true);
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        mUserCallback.onGattConnectionChange(gatt, BlufiCallback.STATUS_SUCCESS, false);
                        gatt.close();
                        mActionCallback.onGattClose(mMeshBlufi.getBlufiClient());
                        break;
                }
            } else {
                mUserCallback.onGattConnectionChange(gatt, status, false);
                gatt.close();
                mActionCallback.onGattClose(mMeshBlufi.getBlufiClient());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mmLog.d(String.format(Locale.ENGLISH, "onServicesDiscovered status=%d", status));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID_SERVICE);
                if (service == null) {
                    mmLog.w("Discover service failed");
                    mUserCallback.onGattServiceDiscover(gatt, -1, UUID_SERVICE);
                    gatt.disconnect();
                    return;
                }
                mUserCallback.onGattServiceDiscover(gatt, BlufiCallback.STATUS_SUCCESS, UUID_SERVICE);

                BluetoothGattCharacteristic writeChar = service.getCharacteristic(UUID_WRITE_CHARACTERISTIC);
                if (writeChar == null) {
                    mmLog.w("Get wite characteristic failed");
                    mUserCallback.onGattCharacteristicDiscover(gatt, -1, UUID_WRITE_CHARACTERISTIC);
                    gatt.disconnect();
                    return;
                }
                mUserCallback.onGattCharacteristicDiscover(gatt, BlufiCallback.STATUS_SUCCESS, UUID_WRITE_CHARACTERISTIC);

                BluetoothGattCharacteristic notifyChar = service.getCharacteristic(UUID_NOTIFICATION_CHARACTERISTIC);
                if (notifyChar == null) {
                    mmLog.w("Get notification characteristic failed");
                    mUserCallback.onGattCharacteristicDiscover(gatt, -1, UUID_NOTIFICATION_CHARACTERISTIC);
                    gatt.disconnect();
                    return;
                }
                gatt.setCharacteristicNotification(notifyChar, true);
                mUserCallback.onGattCharacteristicDiscover(gatt, BlufiCallback.STATUS_SUCCESS,
                        UUID_NOTIFICATION_CHARACTERISTIC);

                BlufiClient blufiClient = new BlufiClient(gatt, writeChar, notifyChar, mActionCallback);
                blufiClient.setDeviceVersion(mMeshBlufi.getMeshVersion());
                mMeshBlufi.setBlufiClient(blufiClient);

                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                if (!gatt.requestMtu(DEFAULT_MTU_LENGTH)) {
                    onMtuChanged(gatt, DEFAULT_MTU_LENGTH, 7001);
                }
            } else {
                gatt.disconnect();
                mUserCallback.onGattServiceDiscover(gatt, status, UUID_SERVICE);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            mmLog.d(String.format(Locale.ENGLISH, "onMtuChanged status=%d, mtu=%d", status, mtu));
            boolean isSamsungAndroid10 = Build.VERSION.SDK_INT == 29
                    && Build.MANUFACTURER.toLowerCase().startsWith("samsung");
            if (status != BluetoothGatt.GATT_SUCCESS || isSamsungAndroid10) {
                mMeshBlufi.getBlufiClient().setPostPackageLengthLimit(18);
            } else {
                mMeshBlufi.getBlufiClient().setPostPackageLengthLimit(mtu - 5);
            }

            mUserCallback.onMtuChanged(gatt, mtu, status);
            mUserCallback.onBlufiClientSet(mMeshBlufi.getBlufiClient());
            onBlufiClientSetComplete();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            mmLog.d("onCharacteristicChanged");
            mMeshBlufi.getBlufiClient().onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            mmLog.d(String.format(Locale.ENGLISH, "onCharacteristicWrite status=%d", status));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mMeshBlufi.getBlufiClient().onCharacteristicWrite(gatt, characteristic, status);
            } else {
                gatt.disconnect();
            }
        }

        private class BlufiCallbackImpl extends BlufiCallback {
            @Override
            public void onNotification(BlufiClient client, int pkgType, int subType, byte[] data) {
                mmLog.d(String.format(Locale.ENGLISH, "onNotification pkgType=%d, subType=%d", pkgType, subType));
                mUserCallback.onNotification(client, pkgType, subType, data);
            }

            @Override
            public void onGattClose(BlufiClient client) {
                mmLog.d("onGattClose");
                mUserCallback.onGattClose(client);
            }

            @Override
            public void onError(BlufiClient client, int errCode) {
                mmLog.w(String.format(Locale.ENGLISH, "onError errCode=%d", errCode));
                mMeshBlufi.getBluetoothGatt().disconnect();

                mUserCallback.onError(client, errCode);
            }

            @Override
            public void onNegotiateSecurityResult(BlufiClient client, int status) {
                mmLog.d(String.format(Locale.ENGLISH, "onNegotiateSecurityResult status=%d", status));
                if (status == STATUS_SUCCESS) {
                    onNegotiateSecurityComplete();
                } else {
                    mMeshBlufi.getBluetoothGatt().disconnect();
                }

                mUserCallback.onNegotiateSecurityResult(client, status);
            }

            @Override
            public void onConfigureResult(BlufiClient client, int status) {
                mmLog.d(String.format(Locale.ENGLISH, "onConfigureResult status=%d", status));
                if (status != STATUS_SUCCESS) {
                    mMeshBlufi.getBluetoothGatt().disconnect();
                }

                mUserCallback.onConfigureResult(client, status);
            }

            @Override
            public void onWifiStateResponse(BlufiClient client, BlufiStatusResponse response) {
                mmLog.d(String.format(Locale.ENGLISH, "onWifiStateResponse %s", response.generateValidInfo()));
                onReceiveWifiState(response);
                mUserCallback.onWifiStateResponse(client, response);
            }

            @Override
            public void onSendMDFCustomData(BlufiClient client, byte[] data, int status) {
                mUserCallback.onSendMDFCustomData(client, data, status);
            }

            @Override
            public void onReceivedCustomData(BlufiClient client, byte[] data) {
                mUserCallback.onReceivedCustomData(client, data);
            }
        }
    }
}

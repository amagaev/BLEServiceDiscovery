import Base64 from '../Base64';
import React from 'react';

export const connectedPeripheral = value => ({
  type: 'CONNECTED_PERIPHERAL',
  value,
});

export const characteristicRxReady = value => ({
  type: 'CHARACTERISTIC_RX_READY',
  value,
});

export const characteristicTxReady = value => ({
  type: 'CHARACTERISTIC_TX_READY',
  value,
});

export const disconnectedPeripheral = () => ({
  type: 'DISCONNECTED_PERIPHERAL',
});

export const changePeripheralStatus = status => ({
  type: 'CHANGE_PERIPHERAL_STATUS',
  status: status,
});

export const characteristicDidRead = value => ({
  type: 'CHARACTERISTIC_DID_READ',
  value,
});

function str2ab(str) {
  var bufView = new Uint8Array(str.length);
  for (var i = 0, strLen = str.length; i < strLen; i++) {
    bufView[i] = str.charCodeAt(i);
  }
  return bufView;
}

function ab2str(buffer) {
  var str = '';
  for (var iii = 0; iii < buffer.byteLength; iii++) {
    str += String.fromCharCode(buffer[iii]);
  }

  return str;
}

export const monitorCharacteristic = characteristic => {
  return (dispatch, getState, DeviceManager) => {
    subscribeToCharacteristic(
      characteristic,
      dispatch,
      getState,
      DeviceManager,
    );
  };
};

var subscription = null;

async function subscribeToCharacteristic(
  characteristic,
  dispatch,
  getState,
  DeviceManager,
) {
  var buffer = new Uint8Array();
  if (subscription) {
    subscription.remove();
  }
  subscription = characteristic.monitor((error, ch) => {
    if (error) {
      console.log(error);
    } else {
      const decodedValue = Base64.atob(ch.value);
      console.log('READ CHAR', decodedValue);
      if (decodedValue === 'EOM') {
        console.log('NOTIFY', ab2str(buffer));
        dispatch(characteristicDidRead(ab2str(buffer)));
        buffer = new Uint8Array();
      } else {
        var newData = str2ab(decodedValue);
        var newBuffer = new Uint8Array(buffer.length + newData.length);
        newBuffer.set(buffer);
        newBuffer.set(newData, buffer.length);
        buffer = newBuffer;
        console.log('READ CHAR', ab2str(buffer));
      }
    }
  });
}

export const readCharacteristic = () => {
  return (dispatch, getState, DeviceManager) => {
    const state = getState();

    state.BLEs.connectedDevice
      .readCharacteristicForService(
        state.BLEs.selectedService.uuid,
        state.BLEs.selectedCharacteristic.uuid,
      )
      .then(characteristic => {
        const decodedValue = Base64.atob(characteristic.value);
        console.log('READ CHAR', decodedValue);
        dispatch(characteristicDidRead(decodedValue));
      });
  };
};

const performTimeConsumingTask = async () => {
  return new Promise(resolve =>
    setTimeout(() => {
      resolve('result');
    }, 10),
  );
};

export const writeCharacteristic = (characteristic, text) => {
  console.log('writing: ', text);
  return async (dispatch, getState, DeviceManager) => {
    // const state = getState();
    text = text;
    let buffer = str2ab(text);
    let packetsize = 512;
    let offset = 0;
    let packetlength = packetsize;
    do {
      if (offset + packetsize > buffer.length) {
        packetlength = buffer.length;
      } else {
        packetlength = offset + packetsize;
      }
      let packet = buffer.slice(offset, packetlength);
      console.log('packet: ', packet);
      let base64packet = Base64.btoa(String.fromCharCode.apply(null, packet));
      characteristic.writeWithoutResponse(base64packet);
      offset += packetsize;
      await performTimeConsumingTask();
    } while (offset < buffer.length);
    await performTimeConsumingTask();
    let base64packet = Base64.btoa(
      String.fromCharCode.apply(null, str2ab('EOM')),
    );
    characteristic.writeWithoutResponse(base64packet);
  };
};

export const connectToPeripheral = () => {
  return async (dispatch, getState, DeviceManager) => {
    const device = await retrievePeripheral(dispatch, getState, DeviceManager);
    await connectPeripheral(device, dispatch, getState, DeviceManager);
    await discoverTransferCharacteristics(
      device,
      dispatch,
      getState,
      DeviceManager,
    );
  };
};

const transferServiceUUID = 'E20A39F4-73F5-4BC4-A12F-17D1AD07A961';
const transferCharacteristicRxUUID = '08590F7E-DB05-467E-8757-72F6FAEB13D4';
const transferCharacteristicTxUUID = '08590F7E-DB05-467E-8757-72F6FAEB13D5';

async function connectPeripheral(device, dispatch, getState, DeviceManager) {
  console.log('CONNECTING');
  dispatch(changePeripheralStatus('Connecting'));
  try {
    const connectedDevice = await device.connect();
    console.log('CONNECTED TO DEVICE');
    DeviceManager.stopDeviceScan();
    dispatch(changePeripheralStatus('Connected'));
    dispatch(connectedPeripheral(connectedDevice));
    return connectedDevice;
  } catch (error) {
    console.log('CAN NOT CONNECT TO DEVICE', error);
    DeviceManager.stopDeviceScan();
    dispatch(changePeripheralStatus('Failed'));
  }
}

const findPeripheral = (dispatch, DeviceManager) =>
  new Promise((resolve, reject) => {
    console.log('SCANNING');
    dispatch(changePeripheralStatus('Scanning'));
    DeviceManager.startDeviceScan(
      [transferServiceUUID],
      null,
      (error, device) => {
        if (error) {
          console.log(error);
          reject(error);
        } else if (device !== null) {
          console.log('FOUND DEVICE');
          DeviceManager.stopDeviceScan();
          resolve(device);
        }
      },
    );
  });

async function retrievePeripheral(dispatch, getState, DeviceManager) {
  console.log('RETRIEVING');

  dispatch(changePeripheralStatus('Idle'));
  dispatch(disconnectedPeripheral());

  const devices = await DeviceManager.connectedDevices([transferServiceUUID]);
  console.log('CONNECTED DEVICES: ', devices.length);
  if (devices.length > 0) {
    return devices[0];
  } else {
    return await findPeripheral(dispatch, DeviceManager);
  }
}

async function discoverTransferCharacteristics(
  device,
  dispatch,
  getState,
  DeviceManager,
) {
  console.log('DISCOVERING ALL SERVICES AND CHARACTERISTICS');

  device = await device.discoverAllServicesAndCharacteristics();
  console.log('DISCOVERED ALL SERVICES AND CHARACTERISTICS');
  dispatch(connectedPeripheral(device));

  const services = await device.services();
  console.log('DISCOVERED SERVICES', services.length);
  services.forEach(s => {
    console.log('UUID', s.uuid);
  });
  var transferService = services.find(s => {
    return s.uuid.toUpperCase() === transferServiceUUID;
  });
  if (transferService) {
    console.log('DISCOVERING CHARACTERISTICS FOR TRANSFER SERVICE');
    const characteristics = await transferService.characteristics();
    console.log('DISCOVERED CHARACTERISTICS', characteristics.length);
    var transferRxCharacteristic = characteristics.find(ch => {
      return ch.uuid.toUpperCase() === transferCharacteristicRxUUID;
    });
    if (transferRxCharacteristic) {
      dispatch(characteristicRxReady(transferRxCharacteristic));
    }
    var transferTxCharacteristic = characteristics.find(ch => {
      return ch.uuid.toUpperCase() === transferCharacteristicTxUUID;
    });
    if (transferTxCharacteristic) {
      dispatch(characteristicTxReady(transferTxCharacteristic));
    }
    if (transferTxCharacteristic && transferRxCharacteristic) {
      dispatch(changePeripheralStatus('Ready'));
      console.log('TRANSFER CHARACTERISTIC READY TO SUBSCRIBE');
    }
  }
}

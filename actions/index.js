import Base64 from '../Base64';
import React from 'react';

import {
  RTCPeerConnection,
  mediaDevices,
  RTCSessionDescription} from 'react-native-webrtc';

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

export const webRTCLocalStreamUrl = value => ({
  type: 'WEBRTC_LOCAL_STREAM_URL',
  value,
});

export const webRTCConnectionStatus = value => ({
  type: 'WEBRTC_CONNECTION_STATUS',
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

function subscribeToCharacteristic(characteristic, onMessageReceived) {
  var buffer = new Uint8Array();
  let subscription = characteristic.monitor((error, ch) => {
    if (error) {
      console.log(error);
    } else {
      const decodedValue = Base64.atob(ch.value);
      //console.log('READ CHAR', decodedValue);
      if (decodedValue === 'EOM') {
        let message = ab2str(buffer);
        console.log('NOTIFY', message);
        onMessageReceived(message);
        buffer = new Uint8Array();
      } else {
        var newData = str2ab(decodedValue);
        var newBuffer = new Uint8Array(buffer.length + newData.length);
        newBuffer.set(buffer);
        newBuffer.set(newData, buffer.length);
        buffer = newBuffer;
        //console.log('READ CHAR', ab2str(buffer));
      }
    }
  });
  return subscription;
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
        //console.log('READ CHAR', decodedValue);
        dispatch(characteristicDidRead(decodedValue));
      });
  };
};

async function writeCharacteristic(device, characteristic, text) {
  text = text;
  let buffer = str2ab(text);
  let mtu = device.mtu;
  console.log('MTU:', mtu);
  if (!mtu) {
    mtu = 23;
  }
  let packetsize = mtu - 3;
  let offset = 0;
  let packetlength = packetsize;
  do {
    if (offset + packetsize > buffer.length) {
      packetlength = buffer.length;
    } else {
      packetlength = offset + packetsize;
    }
    let packet = buffer.slice(offset, packetlength);
    //console.log('packet: ', packet);
    let base64packet = Base64.btoa(String.fromCharCode.apply(null, packet));
    await characteristic.writeWithoutResponse(base64packet);
    offset += packetsize;
  } while (offset < buffer.length);
  let base64packet = Base64.btoa(
    String.fromCharCode.apply(null, str2ab('EOM')),
  );
  await characteristic.writeWithoutResponse(base64packet);
}

var connectedSubscription = null;

export const connectToPeripheral = () => {
  return async (dispatch, getState, DeviceManager) => {
    const device = await retrievePeripheral(dispatch, getState, DeviceManager);
    await connectPeripheral(device, dispatch, getState, DeviceManager);
    if (connectedSubscription) {
      connectedSubscription.remove();
      connectedSubscription = null;
    }
    connectedSubscription = DeviceManager.onDeviceDisconnected(
      device.id,
      (error, device) => {
        console.log('onDeviceDisconnected');
        dispatch(changePeripheralStatus('Idle'));
        dispatch(disconnectedPeripheral());
        connectedSubscription.remove();
        connectedSubscription = null;
        dispatch(connectToPeripheral());
      },
    );
    await device.requestMTU(517);
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

async function createWebRTCOffer(dispatch, yourConn, getState) {
  let isFront = true;

  let sourceInfos = await mediaDevices.enumerateDevices();
  let videoSourceId;
  for (let i = 0; i < sourceInfos.length; i++) {
    const sourceInfo = sourceInfos[i];
    if (
      sourceInfo.kind == 'videoinput' &&
      sourceInfo.facing == (isFront ? 'front' : 'environment')
    ) {
      videoSourceId = sourceInfo.deviceId;
    }
  }
  let stream = await mediaDevices.getUserMedia({
    audio: true,
    video: {
      mandatory: {
        minWidth: 500, // Provide your own width, height and frame rate here
        minHeight: 300,
        minFrameRate: 30,
      },
      facingMode: isFront ? 'user' : 'environment',
      optional: videoSourceId ? [{sourceId: videoSourceId}] : [],
    },
  });
  // Got stream!
  console.log('Set local stream');
  // setup stream listening
  console.log('ADD STREAM');
  yourConn.addStream(stream);
  dispatch(webRTCLocalStreamUrl(stream.toURL()));
  let offer = await yourConn.createOffer();
  await yourConn.setLocalDescription(offer);
  console.log('OFFER IS CREATED');
  //console.log(offer);
  return offer;
}

const awaitMessageFromCharacteristic = (characteristic, timeout) =>
  new Promise((resolve, reject) => {
    var buffer = new Uint8Array();
    var subscription = null;

    setTimeout(() => {
      subscription.remove();
      buffer = new Uint8Array();
      reject({error: 'Timeout'});
    }, timeout);

    subscription = characteristic.monitor((error, ch) => {
      if (error) {
        console.log(error);

        subscription.remove();
        buffer = new Uint8Array();
        reject(error);
      } else {
        const decodedValue = Base64.atob(ch.value);
        //console.log('READ CHAR', decodedValue);
        if (decodedValue === 'EOM') {
          let message = ab2str(buffer);
          //console.log('NOTIFY', message);

          subscription.remove();
          buffer = new Uint8Array();
          resolve(message);
        } else {
          var newData = str2ab(decodedValue);
          var newBuffer = new Uint8Array(buffer.length + newData.length);
          newBuffer.set(buffer);
          newBuffer.set(newData, buffer.length);
          buffer = newBuffer;
          //console.log('READ CHAR', ab2str(buffer));
        }
      }
    });
  });

export const setupWebRTCConnection = () => {
  return async (dispatch, getState, DeviceManager) => {
    const state = getState();
    let device = state.BLEs.connectedPeripheral;
    let transferRxCharacteristic = state.BLEs.transferRxCharacteristic;
    let transferTxCharacteristic = state.BLEs.transferTxCharacteristic;
    var yourConn = new RTCPeerConnection();

    yourConn.onicecandidate = async (event) => {
      console.log('onicecandidate');
      if (event.candidate) {
        var jsonCandidate = JSON.stringify(event.candidate);
        //console.log('TX', transferTxCharacteristic);
        console.log('jsonCandidate');
        await writeCharacteristic(device, transferTxCharacteristic, jsonCandidate);
      }
    };

    let offer = await createWebRTCOffer(dispatch, yourConn);

    console.log('Sending Offer');
    dispatch(webRTCConnectionStatus('Sending Offer'));
    var jsonOffer = JSON.stringify(offer);

    //console.log('TX', transferTxCharacteristic);
    //console.log('jsonOffer', jsonOffer);
    await writeCharacteristic(device, transferTxCharacteristic, jsonOffer);

    console.log('Waiting Answer');
    dispatch(webRTCConnectionStatus('Waiting Answer'));
    let answer = await awaitMessageFromCharacteristic(
      transferRxCharacteristic,
      10000,
    );
    let answerObject = JSON.parse(answer);
    console.log('Answer Received');
    //console.log('SDP: ', answerObject.sdp);
    dispatch(
      webRTCConnectionStatus(`Answer Received, SDP: ${answerObject.sdp}`),
    );
    await yourConn.setRemoteDescription(new RTCSessionDescription(answerObject));
  };
};

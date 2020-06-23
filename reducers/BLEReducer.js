import update from 'immutability-helper';

const INITIAL_STATE = {
  peripheralStatus: 'Idle',
  connectedPeripheral: null,
  transferRxCharacteristic: null,
  transferTxCharacteristic: null,

  webRTCLocalStreamUrl: null,
  webRTCRemoteStreamUrl: null,
  webRTCConnectionStatus: '',
};

const BLEReducer = (state = INITIAL_STATE, action) => {
  switch (action.type) {
    case 'CHARACTERISTIC_DID_READ':
      return update(state, {readCharacteristicValue: {$set: action.value}});
    case 'CONNECTED_PERIPHERAL':
      return update(state, {connectedPeripheral: {$set: action.value}});
    case 'DISCONNECTED_PERIPHERAL':
      state = update(state, {connectedPeripheral: {$set: null}});
      state = update(state, {transferRxCharacteristic: {$set: null}});
      state = update(state, {transferTxCharacteristic: {$set: null}});
      state = update(state, {readCharacteristicValue: {$set: ''}});

      state = update(state, {webRTCConnectionStatus: {$set: ''}});
      return state;
    case 'CHANGE_PERIPHERAL_STATUS':
      return update(state, {peripheralStatus: {$set: action.status}});
    case 'CHARACTERISTIC_RX_READY':
      return update(state, {transferRxCharacteristic: {$set: action.value}});
    case 'CHARACTERISTIC_TX_READY':
      return update(state, {transferTxCharacteristic: {$set: action.value}});
    case 'WEBRTC_LOCAL_STREAM_URL':
      return update(state, {webRTCLocalStreamUrl: {$set: action.value}});
    case 'WEBRTC_REMOTE_STREAM_URL':
      return update(state, {webRTCRemoteStreamUrl: {$set: action.value}});
    case 'WEBRTC_CONNECTION_STATUS':
      return update(state, {webRTCConnectionStatus: {$set: action.value}});
    default:
      return state;
  }
};

export default BLEReducer;

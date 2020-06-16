import update from 'immutability-helper';

const INITIAL_STATE = {
  peripheralStatus: 'Idle',
  connectedPeripheral: null,
  transferRxCharacteristic: null,
  transferTxCharacteristic: null,

  readCharacteristicValue: '',
};

const BLEReducer = (state = INITIAL_STATE, action) => {
  switch (action.type) {
    case 'CHARACTERISTIC_DID_READ':
      return update(state, {readCharacteristicValue: {$set: action.value}});

    case 'CONNECTED_PERIPHERAL':
      return update(state, {connectedPeripheral: {$set: action.value}});
    case 'DISCONNECTED_PERIPHERAL':
      return update(state, {connectedPeripheral: {$set: null}});
    case 'CHANGE_PERIPHERAL_STATUS':
      return update(state, {peripheralStatus: {$set: action.status}});
    case 'CHARACTERISTIC_RX_READY':
      return update(state, {transferRxCharacteristic: {$set: action.value}});
    case 'CHARACTERISTIC_TX_READY':
      return update(state, {transferTxCharacteristic: {$set: action.value}});
    default:
      return state;
  }
};

export default BLEReducer;

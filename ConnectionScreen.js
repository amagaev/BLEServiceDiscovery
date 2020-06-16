import React, {Component} from 'react';
import {SafeAreaView, StyleSheet, Text, Button, View} from 'react-native';
import {connect} from 'react-redux';
import {
  connectToPeripheral,
  monitorCharacteristic,
  writeCharacteristic,
} from './actions';

class ConnectionScreen extends Component {
  componentDidMount() {
    this.props.connectToPeripheral();
  }

  onEchoCommandButtonClick = () => {
    this.props.monitorCharacteristic(this.props.transferRxCharacteristic);
    this.props.writeCharacteristic(this.props.transferTxCharacteristic, 'Echo');
  };

  onConfigCommandButtonClick = () => {
    this.props.monitorCharacteristic(this.props.transferRxCharacteristic);
    this.props.writeCharacteristic(
      this.props.transferTxCharacteristic,
      'Config',
    );
  };

  render() {
    const transferContainer =
      this.props.transferRxCharacteristic &&
      this.props.transferTxCharacteristic ? (
        <>
          <Text style={styles.messageText}>
            {this.props.readCharacteristicValue}
          </Text>
          <View style={styles.buttonContainer}>
            <Button
              title="Send Echo"
              onPress={() => this.onEchoCommandButtonClick()}
            />
            <Button
              title="Get Config"
              onPress={() => this.onConfigCommandButtonClick()}
            />
          </View>
        </>
      ) : null;
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.statusContainer}>
          <View
            style={
              this.props.connectedPeripheral
                ? styles.connectedStatusView
                : styles.disconnectedStatusView
            }
          />
          <Text style={styles.statusText}>{this.props.peripheralStatus}</Text>
        </View>
        {transferContainer}
      </SafeAreaView>
    );
  }
}

function mapStateToProps(state) {
  return {
    peripheralStatus: state.BLEs.peripheralStatus,
    connectedPeripheral: state.BLEs.connectedPeripheral,
    transferRxCharacteristic: state.BLEs.transferRxCharacteristic,
    transferTxCharacteristic: state.BLEs.transferTxCharacteristic,
    readCharacteristicValue: state.BLEs.readCharacteristicValue,
  };
}

const mapDispatchToProps = dispatch => ({
  connectToPeripheral: () => dispatch(connectToPeripheral()),
  monitorCharacteristic: characteristic =>
    dispatch(monitorCharacteristic(characteristic)),
  writeCharacteristic: (characteristic, text) =>
    dispatch(writeCharacteristic(characteristic, text)),
});

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'white',
    flex: 1,
  },
  buttonContainer: {
    backgroundColor: 'white',
    flexDirection: 'row',
    justifyContent: 'center',
    flex: 1,
    marginTop: 30,
  },
  statusContainer: {
    backgroundColor: 'white',
    flexDirection: 'row',
    justifyContent: 'center',
    flex: 1,
    marginTop: 30,
  },
  messageText: {
    fontSize: 16,
    padding: 16,
    textAlign: 'center',
  },
  statusText: {
    fontSize: 16,
    paddingStart: 16,
  },
  connectedStatusView: {
    backgroundColor: 'green',
    width: 20,
    height: 20,
    borderRadius: 10,
  },
  disconnectedStatusView: {
    backgroundColor: 'red',
    width: 20,
    height: 20,
    borderRadius: 10,
  },
});

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(ConnectionScreen);

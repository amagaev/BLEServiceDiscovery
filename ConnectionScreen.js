import React, {Component, useState} from 'react';
import update from 'immutability-helper';
import {
  ScrollView,
  SafeAreaView,
  StyleSheet,
  Text,
  Button,
  View,
} from 'react-native';
import {connect} from 'react-redux';
import {RTCPeerConnection, mediaDevices, RTCView} from 'react-native-webrtc';
import {
  connectToPeripheral,
  monitorCharacteristic,
  writeCharacteristic,
} from './actions';

class ConnectionScreen extends Component {
  constructor(props) {
    super(props);
    this.state = {streamUrl: ''};
  }

  componentDidMount() {
    this.props.connectToPeripheral();
  }

  onEchoCommandButtonClick = () => {
    var yourConn = new RTCPeerConnection();
    let isFront = true;

    mediaDevices.enumerateDevices().then(sourceInfos => {
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
      mediaDevices
        .getUserMedia({
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
        })
        .then(stream => {
          // Got stream!
          console.log('Set local stream');
          //localStream = stream;
          var state = update(this.state, {streamUrl: {$set: stream.toURL()}});
          this.setState(state);
          // setup stream listening
          console.log('ADD STREAM');
          yourConn.addStream(stream);

          yourConn.createOffer().then(offer => {
            yourConn.setLocalDescription(offer).then(() => {
              console.log('OFFER IS CREATED');
              console.log(offer);
              this.props.monitorCharacteristic(
                this.props.transferRxCharacteristic,
              );
              var jsonOffer = JSON.stringify(offer);
              jsonOffer = jsonOffer.replace(/\\n/g, '');
              jsonOffer = jsonOffer.replace(/\\r/g, '');
              this.props.writeCharacteristic(
                this.props.transferTxCharacteristic,
                jsonOffer,
              );
            });
          });
        })
        .catch(error => {
          // Log error
        });
    });
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
          <ScrollView style={styles.scrollView}>
            <Text style={styles.messageText}>
              {this.props.readCharacteristicValue}
            </Text>
          </ScrollView>
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
        <View style={{width: 500, height: 150}}>
          <Text>Your Video</Text>
          <RTCView streamURL={this.state.streamUrl} style={styles.localVideo} />
        </View>
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
  localVideo: {
    backgroundColor: '#f2f2f2',
    height: '100%',
    width: '50%',
  },
});

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(ConnectionScreen);

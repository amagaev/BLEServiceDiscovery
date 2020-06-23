import React, {Component} from 'react';
import {
  ScrollView,
  SafeAreaView,
  StyleSheet,
  Text,
  Button,
  View,
} from 'react-native';
import {connect} from 'react-redux';
import {RTCView} from 'react-native-webrtc';
import {connectToPeripheral, setupWebRTCConnection} from './actions';

class ConnectionScreen extends Component {
  componentDidMount() {
    this.props.connectToPeripheral();
  }

  onStartWebrtcCommandButtonClick = () => {
    this.props.setupWebRTCConnection();
  };

  render() {
    const webrtcContainer = this.props.webRTCLocalStreamUrl ? (
      <>
        <View style={styles.videoContainer}>
          <Text>Your Video</Text>
          <RTCView
            streamURL={this.props.webRTCLocalStreamUrl}
            style={styles.localVideo}
          />
        </View>
      </>
    ) : null;

    const webrtcContainer2 = this.props.webRTCRemoteStreamUrl ? (
      <>
        <View style={styles.videoContainer}>
          <Text> Remote Video</Text>
          <RTCView
            streamURL={this.props.webRTCRemoteStreamUrl}
            style={styles.localVideo}
          />
        </View>
      </>
    ) : null;

    const transferContainer =
      this.props.transferRxCharacteristic &&
      this.props.transferTxCharacteristic ? (
        <>
          <View style={styles.buttonContainer}>
            <Button
              title="Start WebRTC"
              onPress={() => this.onStartWebrtcCommandButtonClick()}
            />
          </View>
        </>
      ) : null;
    return (
      <SafeAreaView style={styles.container}>
        {webrtcContainer}
        {webrtcContainer2}
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

    webRTCLocalStreamUrl: state.BLEs.webRTCLocalStreamUrl,
    webRTCRemoteStreamUrl: state.BLEs.webRTCRemoteStreamUrl,
    webRTCConnectionStatus: state.BLEs.webRTCConnectionStatus,
  };
}

const mapDispatchToProps = dispatch => ({
  connectToPeripheral: () => dispatch(connectToPeripheral()),
  setupWebRTCConnection: () => dispatch(setupWebRTCConnection()),
});

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'white',
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
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
    alignItems: 'center',
    flex: 1,
    marginTop: 30,
  },
  videoContainer: {
    backgroundColor: 'white',
    alignItems: 'center',
    flex: 1,
    marginTop: 30,
    width: '100%',
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

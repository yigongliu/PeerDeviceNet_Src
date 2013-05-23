PeerDeviceNet: Secure Ad-Hoc Peer-Peer Communication Among Computing Devices
============================================================================

source code for PeerDeviceNet core runtime, connection manager and peer connector.


src/PeerDeviceNet_Router:
	PeerDeviceNet kernel
	* the core runtime which handles network detection, peer discovery, peer device connection and group communication.
	* provides 3 layers of APIs(idl/messenger/intents) to access the runtime functions as documented in user_guide.
	* run as a service in a background process.
	* a pure generic kernel without enforcing any kind of connection strategy or GUI.
	* packaged as an android library project, which you can add to your application project's dependencies to gain all functionalities.

src/PeerDeviceNet_Core:
	PeerDeviceNet kernel runtime + ConnectionManager and PeerConnector
	* add ConnectionManager preference GUI to show connected devices and connection parameters.
	* support peer discovery and connection thru network multicast.
	* add PeerConnector(GUI) to support discovery and connection by using camera scanning QR code.
	* implement the full peer-join protocol for discovery and connection as described in design doc.
	* an standalone android app which can be reused directly as a component of another connected mobile app; can be invoked thru android intent (See PeerDeviceNet_Chat for a sample).

src/zxing_client_latest:
	a customized subset of ZXing's android client code, an android library project which PeerDeviceNet_Core uses for QR code scanning.

samples/PeerDeviceNet_Chat:
	a sample chat android app project
	* reuse ConnectionManager/PeerConnector for peer device discovery and connection, via android intent.
	* exchange chat messages with peers, using PeerDeviceNet's three kinds of APIs (idl, messenger,intents).
 

# ADSB_Monitor

This is a simple app for switching the Stratus3 ADS-B receiver to Open/Close Mode, and for displaying the incoming data type. It assumes that the transmitter is using the GDL-90 protocol. Your device must be connected to the ADS-B wifi signal prior to invoking this program. The program looks for the message IDs in the data stream, and counts each message type in real time. It does not perform integrity checks on the data.

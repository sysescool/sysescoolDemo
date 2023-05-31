[TOC]

# [project] WebRTCAndroidSignalServer

a nodejs signal server.

# How to run:

install nodejs, watch nvm github repo get the latest install command:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.3/install.sh | bash
nvm install node
```

cd `../WebRTCAndroidSignalServer` and run `npm install` with the file `package.json`, `package-lock.json` to install dependencies.

cd `../WebRTCAndroidSignalServer/cert` and fake ssl:

```bash
openssl req -newkey rsa:2048 -nodes -keyout rsa_private.key -x509 -days 365 -out cert.pem
```

edit file `../WebRTCAndroidSignalServer/public/peerconnection_onebyone/js/main_connection.js` and file `../WebRTCAndroidSignalServer/public/peerconnection_onebyone/js/main_sdp.js` and file `../WebRTCAndroidSignalServer/public/peerconnection_onebyone/js/main.js`:

```javascript
var pcConfig = {
  'iceServers': [{
	'urls': 'turn:STUN/TURN_DOMAIN:PORT',
    'credential': "PASSWORD",
    'username': "USERNAME"
  }]
};
```

run signal server:

```bash
cd ../WebRTCAndroidSignalServer
# in some linux system, listen lower port need sudo.
sudo $(which node) ./server.js
```